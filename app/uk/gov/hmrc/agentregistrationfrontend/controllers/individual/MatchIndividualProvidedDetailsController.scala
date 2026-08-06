/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentregistrationfrontend.controllers.individual

import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import sttp.model.Uri.UriContext
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.AgentApplicationLimitedCompany
import uk.gov.hmrc.agentregistration.shared.AgentApplicationLlp
import uk.gov.hmrc.agentregistration.shared.AgentApplicationSoleTrader
import uk.gov.hmrc.agentregistration.shared.InternalUserId
import uk.gov.hmrc.agentregistration.shared.LinkId
import uk.gov.hmrc.agentregistration.shared.Nino
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistrationfrontend.action.individual.IndividualActions
import uk.gov.hmrc.agentregistrationfrontend.config.AppConfig
import uk.gov.hmrc.agentregistrationfrontend.connectors.CitizenDetailsConnector
import uk.gov.hmrc.agentregistrationfrontend.forms.ConfirmMatchToIndividualProvidedDetailsForm
import uk.gov.hmrc.agentregistrationfrontend.forms.YesNo
import uk.gov.hmrc.agentregistrationfrontend.model.citizendetails.CitizenDetails
import uk.gov.hmrc.agentregistrationfrontend.services.BusinessPartnerRecordService
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentApplicationService
import uk.gov.hmrc.agentregistrationfrontend.services.individual.IndividualProvideDetailsService
import uk.gov.hmrc.agentregistrationfrontend.views.html.individual.ConfirmMatchToIndividualProvidedDetailsPage
import uk.gov.hmrc.auth.core.ConfidenceLevel

import javax.inject.Inject
import scala.concurrent.Future

class MatchIndividualProvidedDetailsController @Inject() (
  actions: IndividualActions,
  mcc: MessagesControllerComponents,
  view: ConfirmMatchToIndividualProvidedDetailsPage,
  agentApplicationService: AgentApplicationService,
  businessPartnerRecordService: BusinessPartnerRecordService,
  individualProvideDetailsService: IndividualProvideDetailsService,
  citizenDetailsConnector: CitizenDetailsConnector,
  appConfig: AppConfig
)
extends FrontendController(mcc, actions):

  private type DataWithOptionalCitizenDetails = Option[CitizenDetails] *: List[IndividualProvidedDetails] *: AgentApplication *: DataWithAdditionalIdentifiers

  private type DataWithMatchedIndividualProvidedDetails = IndividualProvidedDetails *: DataWithOptionalCitizenDetails

  private def baseAction(
    linkId: LinkId,
    fromIv: Option[Boolean]
  ): ActionBuilderWithData[DataWithMatchedIndividualProvidedDetails] = actions
    .authorisedWithAdditionalIdentifiers
    .refine(implicit request =>
      fromIv match
        case Some(_) => Future.successful(request) // we have been through IV uplift
        case None =>
          if
            request.get[ConfidenceLevel] < ConfidenceLevel.L250
          then
            logger.warn(s"User has confidence level ${request.get[ConfidenceLevel].level}, which is below L250, redirecting to IV uplift")
            redirectToIdentityVerification()
          else
            Future.successful(request)
    )
    .refine(implicit request =>
      agentApplicationService
        .find(linkId)
        .map:
          case Some(agentApplication) => request.add(agentApplication)
          case None => Redirect(AppRoutes.providedetails.ExitController.genericExitPage.url)
    )
    .refine(implicit request =>
      individualProvideDetailsService
        .findAllForMatchingWithApplication(request.get[AgentApplication].agentApplicationId)
        .map:
          case list: List[IndividualProvidedDetails] if list.exists(_.internalUserId.contains(request.get[InternalUserId])) =>
            Redirect(AppRoutes.providedetails.CheckYourAnswersController.show(linkId).url)
          case list: List[IndividualProvidedDetails] if !list.exists(_.internalUserId.isEmpty) =>
            logger.warn("No matching IndividualProvidedDetails record and there are no records left without an internalUserId, so we must exit the user")
            Redirect(AppRoutes.providedetails.ExitController.genericExitPage.url)
          case list: List[IndividualProvidedDetails] => request.add[List[IndividualProvidedDetails]](list)
    )
    .refine(implicit request =>
      (request.get[ConfidenceLevel], request.get[Option[Nino]]) match
        case (cl, Some(nino)) if cl >= ConfidenceLevel.L250 =>
          citizenDetailsConnector
            .getCitizenDetails(nino)
            .map[RequestWithData[DataWithOptionalCitizenDetails]]: (details: CitizenDetails) =>
              request.add[Option[CitizenDetails]](Some(details))
        case (cl, Some(nino)) =>
          logger.warn(s"Insufficient confidence level found in session (${cl}), we cannot trust the nino to use in citizen details, redirecting to manual name matching page")
          Future.successful(Redirect(AppRoutes.providedetails.NameMatchingController.show(linkId).url))
        case (_, None) =>
          logger.warn("No NINO found in session, cannot match to citizen details, redirecting to manual name matching page")
          Future.successful(Redirect(AppRoutes.providedetails.NameMatchingController.show(linkId).url))
    )
    .refine(implicit request =>
      val list: List[IndividualProvidedDetails] = request.get
      val maybeCitizenDetails: Option[CitizenDetails] = request.get[Option[CitizenDetails]]
      val listOfUnclaimedIndividualProvidedDetails: List[IndividualProvidedDetails] = list.filter(_.internalUserId.isEmpty)
      listOfUnclaimedIndividualProvidedDetails.matchCitizenDetailsName(maybeCitizenDetails) match
        case Some(individualProvidedDetails) => request.add[IndividualProvidedDetails](individualProvidedDetails)
        case None =>
          logger.warn(s"No matching IndividualProvidedDetails record found for citizen details name, redirecting to manual name matching page")
          Redirect(AppRoutes.providedetails.NameMatchingController.show(linkId).url)
    )
    .ensure(
      condition =
        implicit request =>
          val individualProvidedDetails: IndividualProvidedDetails = request.get
          !individualProvidedDetails.providedByApplicant.contains(true)
      ,
      resultWhenConditionNotMet =
        implicit request =>
          logger.warn(s"IndividualProvidedDetails record ${request.get[IndividualProvidedDetails]._id} has already been completed by the applicant, redirecting to details already provided page")
          Redirect(AppRoutes.providedetails.ExitController.detailsAlreadyProvided.url)
    )

  def show(
    linkId: LinkId,
    fromIv: Option[Boolean]
  ): Action[AnyContent] = baseAction(linkId, fromIv)
    .async:
      implicit request =>
        val agentApplication: AgentApplication = request.get
        val businessTypeKey: String =
          agentApplication match
            case _: AgentApplicationLlp => "LimitedLiabilityPartnership"
            case _: AgentApplicationLimitedCompany => "LimitedCompany"
            case _: AgentApplicationSoleTrader => "SoleTrader"
            case _: AgentApplication.IsPartnership => "Partnership"
        businessPartnerRecordService
          .getApplicationBusinessPartnerRecord(agentApplication.getUtr)
          .map: optBpr =>
            Ok(
              view(
                form = ConfirmMatchToIndividualProvidedDetailsForm.form,
                individualProvidedDetails = request.get[IndividualProvidedDetails],
                entityName = optBpr.map(_.getEntityName).getOrThrowExpectedDataMissing("BPR is missing for application"),
                businessTypeKey = businessTypeKey,
                linkId = linkId,
                fromIv = fromIv
              )
            )

  def submit(
    linkId: LinkId,
    fromIv: Option[Boolean]
  ): Action[AnyContent] = baseAction(linkId, fromIv)
    .ensureValidForm[YesNo](
      form = ConfirmMatchToIndividualProvidedDetailsForm.form,
      resultToServeWhenFormHasErrors =
        implicit request =>
          formWithErrors =>
            val agentApplication: AgentApplication = request.get
            val businessTypeKey: String =
              agentApplication match
                case _: AgentApplicationLlp => "LimitedLiabilityPartnership"
                case _: AgentApplicationLimitedCompany => "LimitedCompany"
                case _: AgentApplicationSoleTrader => "SoleTrader"
                case _: AgentApplication.IsPartnership => "Partnership"
            businessPartnerRecordService
              .getApplicationBusinessPartnerRecord(agentApplication.getUtr)
              .map: optBpr =>
                BadRequest(
                  view(
                    form = formWithErrors,
                    individualProvidedDetails = request.get[IndividualProvidedDetails],
                    entityName = optBpr.map(_.getEntityName).getOrThrowExpectedDataMissing("BPR is missing for application"), // TODO work out whether we call BPR or change to put it into application
                    businessTypeKey = businessTypeKey,
                    linkId = linkId,
                    fromIv = fromIv
                  )
                )
    )
    .async:
      implicit request =>
        val confirmMatchToIndividualProvidedDetails: YesNo = request.get
        if confirmMatchToIndividualProvidedDetails.toBoolean then
          individualProvideDetailsService
            .claimIndividualProvidedDetails(
              individualProvidedDetails = request.get[IndividualProvidedDetails]
                .copy(
                  passedIv = Some(request.get[ConfidenceLevel] >= ConfidenceLevel.L250)
                ),
              internalUserId = request.get[InternalUserId],
              maybeNino = request.get[Option[Nino]],
              citizenDetails = request.get[Option[CitizenDetails]]
            )
            .map: _ =>
              Redirect(AppRoutes.providedetails.CheckYourAnswersController.show(linkId).url)
        else
          logger.warn(s"User does not agree with the match to IndividualProvidedDetails record ${request.get[IndividualProvidedDetails]._id} from citizen details for user ${request.get[InternalUserId].value}, redirecting to manual name matching page")
          Future.successful(Redirect(AppRoutes.providedetails.NameMatchingController.show(linkId).url))

  private def currentUrl(implicit request: RequestHeader): String = appConfig.thisFrontendRelativeBaseUrl + request.uri

  private def redirectToIdentityVerification()(using request: RequestHeader): Result = Redirect(
    uri"""${appConfig.ivUpliftUrl}?${Map(
        "origin" -> Some("agent-registration-frontend"),
        "confidenceLevel" -> Some(ConfidenceLevel.L250.toString),
        "completionURL" -> Some(s"$currentUrl?fromIv=true"),
        "failureURL" -> Some(s"$currentUrl?fromIv=true")
      )}""".toString
  )

  extension (list: List[IndividualProvidedDetails])
    private def matchCitizenDetailsName(
      maybeCitizenDetails: Option[CitizenDetails]
    )(using request: RequestHeader): Option[IndividualProvidedDetails] =
      maybeCitizenDetails match
        case Some(citizenDetails) =>
          val fullName: String = s"${citizenDetails.firstName.getOrElse("")} ${citizenDetails.lastName.getOrElse("")}"
          val maybeExactMatch: Option[IndividualProvidedDetails] = list.find(individualProvidedDetails =>
            individualProvidedDetails.individualName.value.toLowerCase === fullName.toLowerCase
          )
          maybeExactMatch match
            case Some(individualProvidedDetails) => Some(individualProvidedDetails)
            case None =>
              val surnameMatches: List[IndividualProvidedDetails] = list.filter(individualProvidedDetails =>
                individualProvidedDetails.individualName.value.split(" ")
                  .lastOption.exists(_.toLowerCase === citizenDetails.lastName.getOrElse("").toLowerCase)
              )
              surnameMatches match
                case individualProvidedDetails :: Nil => Some(individualProvidedDetails)
                case Nil => None
                case surnameList: List[IndividualProvidedDetails] =>
                  surnameList.find(individualProvidedDetails =>
                    individualProvidedDetails.individualName.value.split(" ")
                      .headOption.exists(_.toLowerCase === citizenDetails.firstName.getOrElse("").toLowerCase)
                  )
        case None => None
