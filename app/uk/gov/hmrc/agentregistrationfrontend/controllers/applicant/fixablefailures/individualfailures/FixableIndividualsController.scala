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

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.fixablefailures.individualfailures

import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.risking.RiskingOutcomeApplication
import uk.gov.hmrc.agentregistration.shared.risking.RiskingOutcomeIndividual

import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.config.AppConfig
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.services.individual.IndividualProvideDetailsService
import uk.gov.hmrc.agentregistrationfrontend.util.DisplayDate.displayDateForLang
import uk.gov.hmrc.agentregistrationfrontend.views.html.applicant.fixablefailures.individualfailures.FixableIndividualsPage

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FixableIndividualsController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  individualProvideDetailsService: IndividualProvideDetailsService,
  fixableIndividualsPage: FixableIndividualsPage,
  appConfig: AppConfig
)
extends FrontendController(mcc, actions):

  def show: Action[AnyContent] =
    actions
      .getApplicationAfterSentForRisking
      .behindFeatureFlag(appConfig.Features.fixableFailures)
      .refine(
        refineF =
          implicit request =>
            request
              .get[AgentApplication]
              .riskingOutcomeApplication match
              case Some(outcome: RiskingOutcomeApplication.FailedFixable) => request.add[RiskingOutcomeApplication.FailedFixable](outcome)
              case _ =>
                logger.warn("Risking outcome is not fixable. Redirecting to where outcome can be handled.")
                Redirect(AppRoutes.apply.AgentApplicationController.applicationStatus)
      )
      .refine(implicit request =>
        val agentApplication: AgentApplication = request.get
        individualProvideDetailsService.findAllByApplicationId(agentApplication.agentApplicationId).map: individualsList =>
          val fixableList = individualsList.filter(_.hasFixableFailure)

          if (fixableList.nonEmpty)
            val detailsNotProvidedByApplicantList = fixableList.filter(_.detailsNotProvidedByApplicant)
            if (detailsNotProvidedByApplicantList.isEmpty) {
              throw new IllegalStateException(
                s"""[
                   | FixableIndividualsController] All individual details provided by applicant for application ${agentApplication.applicationReference}
                   | this includes individuals with person references: ${detailsNotProvidedByApplicantList.map(_.personReference.value)}, redirecting to
                   | error page""".stripMargin
              )
            }
            else
              request.add[List[IndividualProvidedDetails]](detailsNotProvidedByApplicantList)
          else
            logger.warn("No fixable individuals found. Redirecting to status page.")
            Redirect(AppRoutes.apply.AgentApplicationController.applicationStatus)
      ):
        implicit request =>
          val application = request.get[AgentApplication]
          val correctiveActionExpiryDate: LocalDate = request.get[RiskingOutcomeApplication.FailedFixable].correctiveActionExpiryDate

          Ok(fixableIndividualsPage(
            dateOfDeadline = displayDateForLang(correctiveActionExpiryDate),
            fixableIndividualsList = request.get[List[IndividualProvidedDetails]],
            agentApplication = application
          ))

  extension (ipd: IndividualProvidedDetails)

    private def hasFixableFailure: Boolean = ipd.riskingOutcomeIndividual.exists:
      case RiskingOutcomeIndividual.FailedFixable(_, _) => true
      case _ => false
    private def detailsNotProvidedByApplicant: Boolean = ipd.providedByApplicant.contains(false)
