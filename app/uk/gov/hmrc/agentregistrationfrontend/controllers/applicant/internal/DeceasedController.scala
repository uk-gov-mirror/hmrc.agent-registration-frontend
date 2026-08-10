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

import com.softwaremill.quicklens.*
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.Call
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.agentregistration.shared.*
import uk.gov.hmrc.agentregistration.shared.AgentApplication.IsNotSoleTrader
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.=!=
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.connectors.CitizenDetailsConnector
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.model.citizendetails.DesignatoryDetailsResponse
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentApplicationService

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
class DeceasedController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  citizenDetailsConnector: CitizenDetailsConnector,
  agentApplicationService: AgentApplicationService
)
extends FrontendController(mcc, actions):

  def check(): Action[AnyContent] = actions
    .getApplicationInProgress
    .refine(implicit request =>
      request.agentApplication match
        case a: AgentApplicationSoleTrader => request.add(a)
        case _: IsNotSoleTrader =>
          logger.debug(s"Deceased verification is required only for SoleTrader, this business type is ${request.agentApplication.businessType}. Redirecting to company status check.")
          Redirect(nextCheckEndpoint)
    )
    .ensure(
      condition = _.agentApplicationSoleTrader.isDeceasedCheckRequired,
      resultWhenConditionNotMet =
        implicit request =>
          logger.warn("Deceased verification already done. Redirecting to company status check.")
          Redirect(nextCheckEndpoint)
    )
    .async:
      implicit request =>
        for
          checkResult <-
            request
              .agentApplicationSoleTrader
              .getBusinessDetails
              .nino match
              case Some(nino) =>
                citizenDetailsConnector
                  .getDesignatoryDetails(nino)
                  .map: (designatoryDetailsResponse: DesignatoryDetailsResponse) =>
                    if designatoryDetailsResponse.deceased
                    then CheckResult.Fail
                    else CheckResult.Pass
              case None => Future.successful(CheckResult.Pass) // TODO - confirm this is correct

          _ <- agentApplicationService
            .upsert(request.agentApplicationSoleTrader
              .modify(_.deceasedCheckResult)
              .setTo(Some(checkResult)))
        yield checkResult match
          case CheckResult.Pass => Redirect(nextCheckEndpoint)
          case CheckResult.Fail => Redirect(failedCheckPage)

  private def failedCheckPage: Call = AppRoutes.apply.checkfailed.CanNotConfirmIdentityController.show
  private def nextCheckEndpoint: Call = AppRoutes.apply.internal.UnifiedCustomerRegistryController.populateApplicationIdentifiersFromUcr

  extension (agentApplication: AgentApplicationSoleTrader)
    private def isDeceasedCheckRequired: Boolean = agentApplication.deceasedCheckResult =!= Some(CheckResult.Pass)
