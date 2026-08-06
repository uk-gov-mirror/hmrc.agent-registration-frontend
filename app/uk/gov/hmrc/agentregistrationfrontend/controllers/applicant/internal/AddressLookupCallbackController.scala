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
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.connectors.AddressLookupFrontendConnector
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.model.addresslookup.GetConfirmedAddressResponse
import uk.gov.hmrc.agentregistrationfrontend.model.addresslookup.JourneyId
import uk.gov.hmrc.agentregistrationfrontend.model.agentdetails.AgentCorrespondenceAddressHelper
import uk.gov.hmrc.agentregistrationfrontend.services.applicant.AgentApplicationService

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class AddressLookupCallbackController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  agentApplicationService: AgentApplicationService,
  addressLookUpConnector: AddressLookupFrontendConnector
)(using ec: ExecutionContext)
extends FrontendController(mcc, actions):

  def journeyCallback(id: Option[JourneyId]): Action[AnyContent] = actions
    .getApplicationInProgress
    .refine(request => id.fold(BadRequest("Missing JourneyId in the request from address-lookup-frontend."))(request.add))
    .async:
      implicit request =>
        addressLookUpConnector
          .getConfirmedAddress(request.get[JourneyId]).flatMap: (address: GetConfirmedAddressResponse) =>
            val updatedApplication: AgentApplication = request
              .agentApplication
              .modify(_.agentDetails.each.agentCorrespondenceAddress)
              .setTo(Some(AgentCorrespondenceAddressHelper.fromAddressLookupAddress(address)))
            agentApplicationService
              .upsert(updatedApplication)
              .map: _ =>
                Redirect(AppRoutes.apply.agentdetails.CheckYourAnswersController.show.url)
