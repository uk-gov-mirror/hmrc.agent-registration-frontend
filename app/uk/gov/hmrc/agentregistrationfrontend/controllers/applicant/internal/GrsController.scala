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

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.internal

import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import uk.gov.hmrc.agentregistration.shared.*
import uk.gov.hmrc.agentregistration.shared.AgentApplication.IsNotSoleTrader
import uk.gov.hmrc.agentregistration.shared.businessdetails.BusinessDetailsGeneralPartnership
import uk.gov.hmrc.agentregistration.shared.businessdetails.BusinessDetailsLimitedCompany
import uk.gov.hmrc.agentregistration.shared.businessdetails.BusinessDetailsLlp
import uk.gov.hmrc.agentregistration.shared.businessdetails.BusinessDetailsPartnership
import uk.gov.hmrc.agentregistration.shared.businessdetails.BusinessDetailsScottishPartnership
import uk.gov.hmrc.agentregistration.shared.businessdetails.BusinessDetailsSoleTrader
import uk.gov.hmrc.agentregistration.shared.util.Errors.getOrThrowExpectedDataMissing
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.internal.GrsController.*
import uk.gov.hmrc.agentregistrationfrontend.model.grs.JourneyData
import uk.gov.hmrc.agentregistrationfrontend.model.grs.JourneyId
import uk.gov.hmrc.agentregistrationfrontend.model.grs.RegistrationStatus
import uk.gov.hmrc.agentregistrationfrontend.services.GrsService
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentApplicationService
import uk.gov.hmrc.agentregistrationfrontend.util.Errors

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
class GrsController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  grsService: GrsService,
  agentApplicationService: AgentApplicationService
)
extends FrontendController(mcc, actions):

  private val baseAction: ActionBuilderWithData[DataWithApplication] = actions
    .getApplicationInProgress
    .ensure(
      condition = !_.agentApplication.isGrsDataReceived,
      resultWhenConditionNotMet =
        implicit request =>
          logger.debug("Data from GRS already exists. Redirecting to verify entity.")
          Redirect(AppRoutes.apply.internal.RefusalToDealWithController.check())
    )

  def startJourney(): Action[AnyContent] = baseAction
    .async:
      implicit request =>
        grsService
          .createGrsJourney(
            businessType = request.agentApplication.businessType,
            includeNamePageLabel =
              request.agentApplication match
                case a: AgentApplicationSoleTrader => a.userRole.contains(UserRole.Authorised)
                case _: IsNotSoleTrader => false
          )
          .map(journeyStartUrl => Redirect(journeyStartUrl.value))

  /** This endpoint is called by GRS when a user is navigated back from GRS to this Frontend Service. This is where we get [[GrsJourneyData]], extract
    * [[OldBusinessDetails]] from it and store within [[AgentApplication]]
    */
  def journeyCallback(
    journeyId: Option[JourneyId]
  ): Action[AnyContent] = baseAction
    .refine(implicit request =>
      journeyId.fold {
        logger.error("Missing JourneyId in the request.")
        BadRequest("Missing JourneyId in the request.")
      }(journeyId => request.add(journeyId))
    )
    .async:
      implicit request: (RequestWithData[JourneyId *: DataWithApplication]) =>
        for
          journeyData <- grsService.getJourneyData(request.agentApplication.businessType, request.get[JourneyId])
          result <-
            journeyData.registration.registrationStatus match
              case RegistrationStatus.GrsRegistered =>
                if isSoleTraderWithNoSaUtr(journeyData, request.agentApplication) then
                  Future.successful(Redirect(AppRoutes.apply.checkfailed.SoleTraderHasNoSaUtrController.show))
                else if journeyData.identifiersMatch
                then
                  onGrsRegisteredAndIdentifiersMatch(request.agentApplication, journeyData)
                else
                  Future.successful(Redirect(AppRoutes.apply.checkfailed.UnableToConfirmBusinessDetailsController.show))
              case RegistrationStatus.GrsFailed => Future.successful(Redirect(AppRoutes.apply.checkfailed.UnableToConfirmBusinessDetailsController.show))
              case RegistrationStatus.GrsNotCalled => Future.successful(Redirect(AppRoutes.apply.checkfailed.UnableToConfirmBusinessDetailsController.show))
        yield result

  private def isSoleTraderWithNoSaUtr(
    journeyData: JourneyData,
    agentApplication: AgentApplication
  ): Boolean =
    agentApplication match
      case _: AgentApplicationSoleTrader => journeyData.sautr.isEmpty
      case _: IsNotSoleTrader => false

  private def onGrsRegisteredAndIdentifiersMatch(
    agentApplication: AgentApplication,
    journeyData: JourneyData
  )(using request: RequestHeader): Future[Result] =
    Errors.require(
      journeyData.registration.registrationStatus === RegistrationStatus.GrsRegistered,
      "this function is meant to be called for GrsRegistered case"
    )
    Errors.require(
      journeyData.identifiersMatch,
      "this function is meant to be called when identifiers match"
    )
    val updatedApplication: AgentApplication =
      agentApplication match
        case aa: AgentApplicationSoleTrader =>
          aa
            .copy(
              applicationState = ApplicationState.GrsDataReceived,
              businessDetails = Some(journeyData.asBusinessDetailsSoleTrader)
            )
        case aa: AgentApplicationLlp =>
          aa.copy(
            applicationState = ApplicationState.GrsDataReceived,
            businessDetails = Some(journeyData.asBusinessDetailsLlp)
          )
        case aa: AgentApplicationLimitedCompany =>
          aa.copy(
            applicationState = ApplicationState.GrsDataReceived,
            businessDetails = Some(journeyData.asBusinessDetailsLimitedCompany)
          )
        case aa: AgentApplicationGeneralPartnership =>
          aa.copy(
            applicationState = ApplicationState.GrsDataReceived,
            businessDetails = Some(journeyData.asBusinessDetailsGeneralPartnership)
          )
        case aa: AgentApplicationLimitedPartnership =>
          aa.copy(
            applicationState = ApplicationState.GrsDataReceived,
            businessDetails = Some(journeyData.asBusinessDetailsPartnership)
          )
        case aa: AgentApplicationScottishLimitedPartnership =>
          aa.copy(
            applicationState = ApplicationState.GrsDataReceived,
            businessDetails = Some(journeyData.asBusinessDetailsPartnership)
          )
        case aa: AgentApplicationScottishPartnership =>
          aa.copy(
            applicationState = ApplicationState.GrsDataReceived,
            businessDetails = Some(journeyData.asBusinessScottishPartnership)
          )

    agentApplicationService
      .upsert(updatedApplication)
      .map: _ =>
        Redirect(AppRoutes.apply.internal.RefusalToDealWithController.check())

object GrsController:

  extension (journeyData: JourneyData)

    def asBusinessDetailsSoleTrader: BusinessDetailsSoleTrader = BusinessDetailsSoleTrader(
      safeId = journeyData.registration.registeredBusinessPartnerId.getOrThrowExpectedDataMissing("registration.registeredBusinessPartnerId"),
      saUtr = journeyData.sautr.getOrThrowExpectedDataMissing("sautr"),
      fullName = journeyData.fullName.getOrThrowExpectedDataMissing("fullName"),
      dateOfBirth = journeyData.dateOfBirth.getOrThrowExpectedDataMissing("dateOfBirth"),
      nino = journeyData.nino,
      trn = journeyData.trn
    )

    def asBusinessDetailsLlp: BusinessDetailsLlp = BusinessDetailsLlp(
      safeId = journeyData.registration.registeredBusinessPartnerId.getOrThrowExpectedDataMissing("registration.registeredBusinessPartnerId"),
      saUtr = journeyData.sautr.getOrThrowExpectedDataMissing("sautr"),
      companyProfile = journeyData.companyProfile.getOrThrowExpectedDataMissing("companyProfile")
    )

    def asBusinessDetailsLimitedCompany: BusinessDetailsLimitedCompany = BusinessDetailsLimitedCompany(
      safeId = journeyData.registration.registeredBusinessPartnerId.getOrThrowExpectedDataMissing("registration.registeredBusinessPartnerId"),
      ctUtr = journeyData.ctutr.getOrThrowExpectedDataMissing("ctutr"),
      companyProfile = journeyData.companyProfile.getOrThrowExpectedDataMissing("companyProfile")
    )

    def asBusinessDetailsPartnership: BusinessDetailsPartnership = BusinessDetailsPartnership(
      safeId = journeyData.registration.registeredBusinessPartnerId.getOrThrowExpectedDataMissing("registration.registeredBusinessPartnerId"),
      saUtr = journeyData.sautr.getOrThrowExpectedDataMissing("sautr"),
      companyProfile = journeyData.companyProfile.getOrThrowExpectedDataMissing("companyProfile"),
      postcode = journeyData.postcode.getOrThrowExpectedDataMissing("postcode")
    )

    def asBusinessScottishPartnership: BusinessDetailsScottishPartnership = BusinessDetailsScottishPartnership(
      safeId = journeyData.registration.registeredBusinessPartnerId.getOrThrowExpectedDataMissing("registration.registeredBusinessPartnerId"),
      saUtr = journeyData.sautr.getOrThrowExpectedDataMissing("sautr"),
      postcode = journeyData.postcode.getOrThrowExpectedDataMissing("postcode")
    )

    def asBusinessDetailsGeneralPartnership: BusinessDetailsGeneralPartnership = BusinessDetailsGeneralPartnership(
      safeId = journeyData.registration.registeredBusinessPartnerId.getOrThrowExpectedDataMissing("registration.registeredBusinessPartnerId"),
      saUtr = journeyData.sautr.getOrThrowExpectedDataMissing("sautr"),
      postcode = journeyData.postcode.getOrThrowExpectedDataMissing("postcode")
    )
