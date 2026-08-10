/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.agentregistrationfrontend.action.individual

import play.api.mvc.*
import play.api.mvc.Results.NotFound
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.BusinessPartnerRecordResponse
import uk.gov.hmrc.agentregistration.shared.InternalUserId
import uk.gov.hmrc.agentregistration.shared.LinkId
import uk.gov.hmrc.agentregistration.shared.Nino
import uk.gov.hmrc.agentregistration.shared.SaUtr
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.risking.IndividualFix
import uk.gov.hmrc.agentregistration.shared.risking.IndividualFix._10.IndividualDetailsFix
import uk.gov.hmrc.agentregistration.shared.risking.RiskingOutcomeApplication
import uk.gov.hmrc.agentregistration.shared.risking.RiskingOutcomeIndividual
import uk.gov.hmrc.agentregistration.shared.risking.RiskingProgress
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.=!=
import uk.gov.hmrc.agentregistrationfrontend.action.ActionBuilders.refineFutureEither
import uk.gov.hmrc.agentregistrationfrontend.action.ActionBuilders.refineUnion
import uk.gov.hmrc.agentregistrationfrontend.action.ActionBuildersWithData
import uk.gov.hmrc.agentregistrationfrontend.action.RequestWithDataCt
import uk.gov.hmrc.agentregistrationfrontend.config.AppConfig
import uk.gov.hmrc.agentregistrationfrontend.controllers.AppRoutes
import uk.gov.hmrc.agentregistrationfrontend.services.BusinessPartnerRecordService
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentApplicationService
import uk.gov.hmrc.agentregistrationfrontend.services.individual.IndividualProvideDetailsService
import uk.gov.hmrc.agentregistrationfrontend.services.individual.IndividualRiskingService
import uk.gov.hmrc.agentregistrationfrontend.util.RequestAwareLogging
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

object IndividualActions:

  export uk.gov.hmrc.agentregistrationfrontend.action.Actions.*

  type DataWithAuth = (InternalUserId, Credentials)
  type RequestWithAuth = RequestWithData[DataWithAuth]
  type RequestWithAuthCt[ContentType] = RequestWithDataCt[ContentType, DataWithAuth]

  type DataWithAuthAndCl = ConfidenceLevel *: DataWithAuth
  type RequestWithAuthAndCl = RequestWithData[DataWithAuthAndCl]
  type RequestWithAuthAndClCt[ContentType] = RequestWithDataCt[ContentType, DataWithAuthAndCl]

  type DataWithAdditionalIdentifiers = Option[Nino] *: Option[SaUtr] *: DataWithAuthAndCl
  type RequestWithAdditionalIdentifiers = RequestWithData[DataWithAdditionalIdentifiers]
  type RequestWithAdditionalIdentifiersCt[ContentType] = RequestWithDataCt[ContentType, DataWithAdditionalIdentifiers]

  private type DataWithApplicationFromLinkId = AgentApplication *: DataWithAuthAndCl

  type DataWithIndividualProvidedDetails = IndividualProvidedDetails *: DataWithApplicationFromLinkId
  type RequestWithIndividualProvidedDetails = RequestWithData[DataWithIndividualProvidedDetails]
  type DataWithIndividualProvidedDetailsCt[ContentType] = RequestWithDataCt[ContentType, DataWithIndividualProvidedDetails]

  type DataWithRiskingProgress = RiskingProgress *: DataWithIndividualProvidedDetails

  type DataWithRiskingOutcome = BusinessPartnerRecordResponse *: RiskingOutcomeIndividual *: RiskingOutcomeApplication *: DataWithIndividualProvidedDetails
  type DataWithFailedFixable =
    BusinessPartnerRecordResponse *: RiskingOutcomeIndividual.FailedFixable *: RiskingOutcomeApplication *: DataWithIndividualProvidedDetails
  type DataWithIndividualDetailsFix = IndividualDetailsFix *: DataWithFailedFixable

@Singleton
class IndividualActions @Inject (
  appConfig: AppConfig
)(
  defaultActionBuilder: DefaultActionBuilder,
  individualAuthorisedRefiner: IndividualAuthRefiner,
  agentApplicationService: AgentApplicationService,
  individualProvideDetailsService: IndividualProvideDetailsService,
  individualRiskingService: IndividualRiskingService,
  businessPartnerRecordService: BusinessPartnerRecordService
)(using ExecutionContext)
extends RequestAwareLogging:

  export ActionBuildersWithData.*
  export IndividualActions.*

  val action: ActionBuilderWithData[EmptyTuple] = defaultActionBuilder
    .refineUnion(request => RequestWithDataCt.empty(request))

  val authorised: ActionBuilderWithData[DataWithAuthAndCl] = action
    .refineFutureEither(individualAuthorisedRefiner.refineIntoRequestWithAuth)

  val authorisedWithAdditionalIdentifiers: ActionBuilderWithData[DataWithAdditionalIdentifiers] = action
    .refineFutureEither(individualAuthorisedRefiner.refineIntoRequestWithAdditionalIdentifiers)

  def authorisedWithIndividualProvidedDetails(linkId: LinkId): ActionBuilderWithData[DataWithIndividualProvidedDetails] = authorised
    .refine(implicit request =>
      agentApplicationService
        .find(linkId)
        .map:
          case Some(agentApplication) if agentApplication.isAfterSentForRisking =>
            Redirect(AppRoutes.providedetails.riskingprogress.RiskingProgressController.show(linkId))
          case Some(agentApplication) => request.add[AgentApplication](agentApplication)
          case None => Redirect(AppRoutes.providedetails.ExitController.genericExitPage.url)
    )
    .refine(implicit request =>
      individualProvideDetailsService
        .findAllForMatchingWithApplication(request.get[AgentApplication].agentApplicationId)
        .map[RequestWithData[DataWithIndividualProvidedDetails] | Result]:
          case list: List[IndividualProvidedDetails] =>
            list
              .find(_.internalUserId.contains(request.get[InternalUserId]))
              .map(request.add[IndividualProvidedDetails])
              .getOrElse(
                Redirect(AppRoutes.providedetails.MatchIndividualProvidedDetailsController.show(linkId, fromIv = None))
              )
    )

  def authorisedWithRiskingProgress(linkId: LinkId): ActionBuilderWithData[DataWithRiskingProgress] = authorised
    .refine(implicit request =>
      agentApplicationService
        .find(linkId)
        .map:
          case Some(agentApplication) if agentApplication.isAfterSentForRisking => request.add[AgentApplication](agentApplication)
          case Some(_) => Redirect(AppRoutes.providedetails.CheckYourAnswersController.show(linkId))
          case None => Redirect(AppRoutes.providedetails.ExitController.genericExitPage.url)
    )
    .refine(implicit request =>
      individualProvideDetailsService
        .findAllForMatchingWithApplication(request.get[AgentApplication].agentApplicationId)
        .map[RequestWithData[DataWithIndividualProvidedDetails] | Result]:
          case list: List[IndividualProvidedDetails] =>
            list
              .find(_.internalUserId.contains(request.get[InternalUserId]))
              .map(request.add[IndividualProvidedDetails])
              .getOrElse(
                Redirect(AppRoutes.providedetails.MatchIndividualProvidedDetailsController.show(linkId, fromIv = None))
              )
    )
    .refine(implicit request =>
      individualRiskingService
        .getRiskingProgress(request.get[IndividualProvidedDetails].personReference)
        .map: (riskingProgress: RiskingProgress) =>
          request.add[RiskingProgress](riskingProgress)
    )

  def authorisedWithRiskingOutcome(linkId: LinkId): ActionBuilderWithData[DataWithRiskingOutcome] = authorised
    .behindFeatureFlag(appConfig.Features.fixableFailures)
    .refine(implicit request =>
      agentApplicationService
        .find(linkId)
        .map:
          case Some(agentApplication) if agentApplication.isAfterSentForRisking => request.add[AgentApplication](agentApplication)
          case Some(_) => Redirect(AppRoutes.providedetails.CheckYourAnswersController.show(linkId))
          case None => Redirect(AppRoutes.providedetails.ExitController.genericExitPage.url)
    )
    .refine(implicit request =>
      individualProvideDetailsService
        .findAllForMatchingWithApplication(request.get[AgentApplication].agentApplicationId)
        .map[RequestWithData[DataWithIndividualProvidedDetails] | Result]:
          case list: List[IndividualProvidedDetails] =>
            list
              .find(_.internalUserId.contains(request.get[InternalUserId]))
              .map(request.add[IndividualProvidedDetails])
              .getOrElse(
                NotFound
              )
    )
    .refine(implicit request =>
      val agentApplication: AgentApplication = request.get
      agentApplication.riskingOutcomeApplication match
        case Some(riskingOutcomeApplication) => request.add[RiskingOutcomeApplication](riskingOutcomeApplication)
        case None =>
          logger.info("Risking outcome for application not found.")
          NotFound
    )
    .refine(implicit request =>
      val individualProvidedDetails: IndividualProvidedDetails = request.get
      individualProvidedDetails.riskingOutcomeIndividual match
        case Some(riskingOutcomeIndividual) => request.add[RiskingOutcomeIndividual](riskingOutcomeIndividual)
        case None =>
          logger.info("Risking outcome for individual not found.")
          NotFound
    )
    .refine:
      implicit request =>
        businessPartnerRecordService
          .getApplicationBusinessPartnerRecord(request.agentApplication.getUtr)
          .map:
            case Some(bpr) => request.add[BusinessPartnerRecordResponse](bpr)
            case _ => throw new IllegalStateException(s"Business Partner Record not found for application with UTR: ${request.agentApplication.getUtr}")

  def authorisedWithFailedFixable(linkId: LinkId): ActionBuilderWithData[DataWithFailedFixable] = authorisedWithRiskingOutcome(linkId)
    .refine:
      implicit request: (RequestWithData[DataWithRiskingOutcome]) =>
        val confirmationUrl: String = AppRoutes.providedetails.riskingoutcome.fixablefailures.IndividualConfirmationController.show(linkId).url
        val individualRiskingOutcome: RiskingOutcomeIndividual = request.get
        individualRiskingOutcome match
          case outcome @ RiskingOutcomeIndividual.FailedFixable(fixes: Seq[IndividualFix], declarationAgreed: Boolean) =>
            if request.uri =!= confirmationUrl && fixes.forall(_.isConfirmed.contains(true)) && declarationAgreed
            then
              logger.info("Risking outcome for individual has already been fixed. Redirecting to confirmation page.")
              Redirect(AppRoutes.providedetails.riskingoutcome.fixablefailures.IndividualConfirmationController.show(linkId))
            else
              request.replace[RiskingOutcomeIndividual, RiskingOutcomeIndividual.FailedFixable](outcome)
          case _ =>
            logger.info("Risking outcome for individual is not fixable. Redirecting to where outcome can be handled.")
            Redirect(AppRoutes.providedetails.riskingoutcome.RiskingOutcomeController.show(linkId))

  def authorisedWithFixableDetails(linkId: LinkId): ActionBuilderWithData[DataWithIndividualDetailsFix] = authorisedWithFailedFixable(linkId)
    .refine:
      implicit request: (RequestWithData[DataWithFailedFixable]) =>
        val individualRiskingOutcome: RiskingOutcomeIndividual.FailedFixable = request.get
        individualRiskingOutcome.fixes.collectFirst { case fix: IndividualDetailsFix => fix } match
          case Some(individualFix) => request.add[IndividualDetailsFix](individualFix)
          case None =>
            logger.info("Risking outcome for individual does not require individual details to be provided, redirecting to fixable task list.")
            Redirect(AppRoutes.providedetails.riskingoutcome.fixablefailures.FixableTaskListController.show(linkId))
