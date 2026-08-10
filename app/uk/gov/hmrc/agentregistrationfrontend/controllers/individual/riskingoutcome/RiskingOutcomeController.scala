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

package uk.gov.hmrc.agentregistrationfrontend.controllers.individual.riskingoutcome

import play.api.mvc.*
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.AgentApplicationSoleTrader
import uk.gov.hmrc.agentregistration.shared.BusinessPartnerRecordResponse
import uk.gov.hmrc.agentregistration.shared.LinkId
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.risking.RiskedIndividual
import uk.gov.hmrc.agentregistration.shared.risking.RiskingOutcomeApplication
import uk.gov.hmrc.agentregistration.shared.risking.RiskingOutcomeIndividual
import uk.gov.hmrc.agentregistrationfrontend.action.individual.IndividualActions
import uk.gov.hmrc.agentregistrationfrontend.controllers.individual.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.util.DisplayDate.displayDateForLang
import uk.gov.hmrc.agentregistrationfrontend.views.html.individual.riskingoutcome.FailedFixablePage
import uk.gov.hmrc.agentregistrationfrontend.views.html.individual.riskingprogress.FailedNonFixablePage
import uk.gov.hmrc.agentregistrationfrontend.views.html.individual.riskingprogress.IndividualConfirmationPage

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiskingOutcomeController @Inject() (
  actions: IndividualActions,
  mcc: MessagesControllerComponents,
  individualConfirmationPage: IndividualConfirmationPage,
  failedNonFixablePage: FailedNonFixablePage,
  failedFixablePage: FailedFixablePage
)
extends FrontendController(mcc, actions):

  def show(linkId: LinkId): Action[AnyContent] =
    actions.authorisedWithRiskingOutcome(linkId):
      implicit request =>
        val riskingOutcomeApplication: RiskingOutcomeApplication = request.get
        val riskingOutcomeIndividual: RiskingOutcomeIndividual = request.get
        val entityName: String = request.get[BusinessPartnerRecordResponse].getEntityName
        riskingOutcomeApplication match
          case _: RiskingOutcomeApplication.FailedNonFixable =>
            riskingOutcomeIndividual match
              case individualOutcome: RiskingOutcomeIndividual.FailedNonFixable =>
                val riskedIndividual: RiskedIndividual = RiskedIndividual(
                  personReference = request.get[IndividualProvidedDetails].personReference,
                  individualName = request.get[IndividualProvidedDetails].individualName,
                  failures = individualOutcome.failures
                )
                Ok(failedNonFixablePage(
                  riskedIndividual = riskedIndividual,
                  agentApplication = request.agentApplication,
                  entityName = entityName
                ))
              case _: RiskingOutcomeIndividual.FailedFixable => renderConfirmationPage(request.agentApplication, entityName) // this individual has not failed non-fixable, so render the confirmation page
              case RiskingOutcomeIndividual.Approved => renderConfirmationPage(request.agentApplication, entityName) // this individual has not failed non-fixable, so render the confirmation page
          case riskingOutcomeApplication: RiskingOutcomeApplication.FailedFixable =>
            riskingOutcomeIndividual match
              case individualOutcome: RiskingOutcomeIndividual.FailedFixable =>
                if individualOutcome.declarationAgreed && individualOutcome.fixes.forall(_.isConfirmed.contains(true))
                then
                  logger.debug("This individual has failed fixable, but has fixed everything")
                  Redirect(AppRoutes.providedetails.riskingoutcome.fixablefailures.IndividualConfirmationController.show(linkId)) // this individual has failed fixable, but has fixed everything
                else
                  Ok(failedFixablePage(
                    linkId = linkId,
                    entityName = entityName,
                    correctiveActionExpiryDate = displayDateForLang(riskingOutcomeApplication.correctiveActionExpiryDate),
                    actualDecisionDate = displayDateForLang(riskingOutcomeApplication.actualDecisionDate)
                  ))
              case RiskingOutcomeIndividual.Approved => renderConfirmationPage(request.agentApplication, entityName) // this individual has not failed fixable, so render the confirmation page
              case _: RiskingOutcomeIndividual.FailedNonFixable =>
                throw new IllegalStateException(
                  "This individual has failed non-fixable, but the application outcome was failed fixable - this should not be possible"
                )
          case riskingOutcomeApplication: RiskingOutcomeApplication.Approved => renderConfirmationPage(request.agentApplication, entityName) // any other outcome renders the confirmation page

  private def renderConfirmationPage(
    agentApplication: AgentApplication,
    entityName: String
  )(using RequestHeader) =
    val applicantName = agentApplication.getApplicantContactDetails.applicantName
    Ok(individualConfirmationPage(
      applicantName = applicantName.value,
      entityName = entityName,
      isSoleTraderOwner = agentApplication.isSoleTraderOwner
    ))

  extension (agentApplication: AgentApplication)
    def isSoleTraderOwner: Boolean =
      agentApplication match
        case a: AgentApplicationSoleTrader => a.isOwner
        case _ => false
