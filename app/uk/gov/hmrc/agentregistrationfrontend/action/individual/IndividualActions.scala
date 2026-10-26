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
import uk.gov.hmrc.agentregistration.shared.ApplicationState
import uk.gov.hmrc.agentregistration.shared.BusinessPartnerRecordResponse
import uk.gov.hmrc.agentregistration.shared.InternalUserId
import uk.gov.hmrc.agentregistration.shared.LinkId
import uk.gov.hmrc.agentregistration.shared.Nino
import uk.gov.hmrc.agentregistration.shared.SaUtr
import uk.gov.hmrc.agentregistration.shared.individual.IndividualNino
import uk.gov.hmrc.agentregistration.shared.individual.IndividualNino.FromAuth
import uk.gov.hmrc.agentregistration.shared.individual.IndividualNino.Provided
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.risking.IndividualFix
import uk.gov.hmrc.agentregistration.shared.risking.IndividualFix._10.IndividualDetailsFix
import uk.gov.hmrc.agentregistration.shared.risking.RiskingOutcomeApplication
import uk.gov.hmrc.agentregistration.shared.risking.RiskingOutcomeIndividual
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.=!=
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistrationfrontend.action.ActionBuilders.refineFutureEither
import uk.gov.hmrc.agentregistrationfrontend.action.ActionBuilders.refineUnion
import uk.gov.hmrc.agentregistrationfrontend.action.ActionBuildersWithData
import uk.gov.hmrc.agentregistrationfrontend.action.RequestWithDataCt
import uk.gov.hmrc.agentregistrationfrontend.controllers.AppRoutes
import uk.gov.hmrc.agentregistrationfrontend.services.BusinessPartnerRecordService
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentApplicationService
import uk.gov.hmrc.agentregistrationfrontend.services.individual.IndividualProvideDetailsService
import uk.gov.hmrc.agentregistrationfrontend.util.RequestAwareLogging
import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

object IndividualActions:

  export uk.gov.hmrc.agentregistrationfrontend.action.Actions.*

  /** Checkpoints of the individual journey, one alias per stage a controller can be at. Each alias extends its parent in the tree below (rightmost element =
    * added earliest); controllers pick single elements with `request.get[T]`.
    *
    * {{{
    * DataWithAuth                            InternalUserId, Credentials
    * └ DataWithAuthAndCl                     + ConfidenceLevel
    *   ├ DataWithTaxIds                      + Option[SaUtr], Option[Nino]
    *   └ DataWithIndividualProvidedDetails   + AgentApplication, IndividualProvidedDetails
    *     ├ DataWithRiskingProgress           + RiskingProgress
    *     └ DataWithRiskingOutcomes           + RiskingOutcomeApplication, RiskingOutcomeIndividual, BusinessPartnerRecordResponse
    *       └ DataWithFixableOutcomes         both outcomes narrowed to FailedFixable
    *         └ DataWithIndividualDetailsFix  + IndividualDetailsFix
    * }}}
    */
  type DataWithAuth = (InternalUserId, Credentials)
  type DataWithAuthAndCl = ConfidenceLevel *: DataWithAuth
  type DataWithTaxIds = Option[Nino] *: Option[SaUtr] *: DataWithAuthAndCl
  type DataWithIndividualProvidedDetails = IndividualProvidedDetails *: AgentApplication *: DataWithAuthAndCl
  type DataWithIndividualProvidedDetailsAndTaxIds = IndividualProvidedDetails *: AgentApplication *: DataWithTaxIds
  type DataWithRiskingOutcomes =
    BusinessPartnerRecordResponse *: RiskingOutcomeIndividual *: RiskingOutcomeApplication *: DataWithIndividualProvidedDetailsAndTaxIds
  type DataWithFixableOutcomes =
    BusinessPartnerRecordResponse *: RiskingOutcomeIndividual.FailedFixable *: RiskingOutcomeApplication.FailedFixable *: DataWithIndividualProvidedDetailsAndTaxIds
  type DataWithIndividualDetailsFix = IndividualDetailsFix *: DataWithFixableOutcomes

@Singleton
class IndividualActions @Inject(
) (
  defaultActionBuilder: DefaultActionBuilder,
  individualAuthorisedRefiner: IndividualAuthRefiner,
  agentApplicationService: AgentApplicationService,
  individualProvideDetailsService: IndividualProvideDetailsService,
  businessPartnerRecordService: BusinessPartnerRecordService
)(using ExecutionContext)
extends RequestAwareLogging:

  export ActionBuildersWithData.*
  export IndividualActions.*

  val action: ActionBuilderWithData[EmptyTuple] = defaultActionBuilder
    .refineUnion(request => RequestWithDataCt.empty(request))

  val authorised: ActionBuilderWithData[DataWithAuthAndCl] = action
    .refineFutureEither(individualAuthorisedRefiner.refineIntoRequestWithAuth)

  val authorisedWithAdditionalIdentifiers: ActionBuilderWithData[DataWithTaxIds] = action
    .refineFutureEither(individualAuthorisedRefiner.refineIntoRequestWithAdditionalIdentifiers)

  def authorisedWithIndividualProvidedDetails(linkId: LinkId): ActionBuilderWithData[DataWithIndividualProvidedDetails] = authorised
    .refine(implicit request =>
      agentApplicationService
        .find(linkId)
        .map:
          case Some(agentApplication) if agentApplication.applicationState === ApplicationState.Expired =>
            logger.info(s"User has entered application at deep url and application for linkId $linkId has expired, redirecting to start where expired is handled.")
            Redirect(AppRoutes.providedetails.StartController.start(linkId))
          case Some(agentApplication) if agentApplication.riskingOutcomeApplication.isDefined =>
            Redirect(AppRoutes.providedetails.riskingoutcome.RiskingOutcomeController.show(linkId))
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

  def authorisedWithRiskingOutcome(linkId: LinkId): ActionBuilderWithData[DataWithRiskingOutcomes] = authorisedWithAdditionalIdentifiers
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
        .map[RequestWithData[DataWithIndividualProvidedDetailsAndTaxIds] | Result]:
          case list: List[IndividualProvidedDetails] =>
            list
              .find(individual =>
                if
                  individual.internalUserId.contains(request.get[InternalUserId])
                then
                  logger.info(s"Matched individual by internalUserId to risking outcome for person reference ${individual.personReference.value}")
                  true
                else if
                  matchSafeNino(
                    request.get[Option[Nino]],
                    request.get[ConfidenceLevel],
                    individual.individualNino
                  )
                then
                  logger.info(s"Matched individual by verified NINO to risking outcome for person reference ${individual.personReference.value}")
                  true
                else false
              )
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

  def authorisedWithFailedFixable(linkId: LinkId): ActionBuilderWithData[DataWithFixableOutcomes] = authorisedWithRiskingOutcome(linkId)
    .refine:
      implicit request =>
        val riskingOutcomeApplication: RiskingOutcomeApplication = request.get
        riskingOutcomeApplication match
          case outcome: RiskingOutcomeApplication.FailedFixable => request.replace[RiskingOutcomeApplication, RiskingOutcomeApplication.FailedFixable](outcome)
          case _ =>
            logger.warn(s"Risking outcome for application is not fixable, was: $riskingOutcomeApplication. Redirecting to where outcome can be handled.")
            Redirect(AppRoutes.providedetails.riskingoutcome.RiskingOutcomeController.show(linkId))
    .refine:
      implicit request =>
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
      implicit request =>
        val individualRiskingOutcome: RiskingOutcomeIndividual.FailedFixable = request.get
        individualRiskingOutcome.fixes.collectFirst { case fix: IndividualDetailsFix => fix } match
          case Some(individualFix) => request.add[IndividualDetailsFix](individualFix)
          case None =>
            logger.info("Risking outcome for individual does not require individual details to be provided, redirecting to fixable task list.")
            Redirect(AppRoutes.providedetails.riskingoutcome.fixablefailures.FixableTaskListController.show(linkId))

  private def matchSafeNino(
    ninoFromAuth: Option[Nino],
    confidenceLevel: ConfidenceLevel,
    individualNino: Option[IndividualNino]
  ): Boolean = {
    (confidenceLevel, ninoFromAuth, individualNino) match
      case (cl, Some(nino), Some(Provided(individualNino))) if cl >= ConfidenceLevel.L250 => nino.value.take(8) === individualNino.value.take(8) // compare without suffix letter, as auth nino may not have suffix letter
      case (cl, Some(nino), Some(FromAuth(individualNino))) if cl >= ConfidenceLevel.L250 => nino.value.take(8) === individualNino.value.take(8) // compare without suffix letter, as auth nino may not have suffix letter
      case _ => false
  }
