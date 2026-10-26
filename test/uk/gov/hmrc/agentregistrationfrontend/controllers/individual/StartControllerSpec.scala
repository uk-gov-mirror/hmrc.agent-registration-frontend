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

import com.softwaremill.quicklens.modify
import play.api.libs.ws.DefaultBodyReadables.*
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.ApplicationState
import uk.gov.hmrc.agentregistration.shared.LinkId
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ControllerSpec
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.AgentRegistrationStubs

class StartControllerSpec
extends ControllerSpec:

  private val linkId: LinkId = tdAll.linkId
  private val path: String = s"/agent-registration/provide-details/start/${linkId.value}"

  object agentApplication:

    val inComplete: AgentApplication =
      tdAll
        .agentApplicationLlpSections
        .sectionContactDetails
        .afterEmailAddressVerified
    val complete: AgentApplication = inComplete
      .modify(_.applicationState)
      .setTo(ApplicationState.SentForRisking)
      .modify(_.submittedAt)
      .setTo(Some(tdAll.nowAsInstant))
    val riskingComplete: AgentApplication = complete
      .modify(_.applicationState)
      .setTo(ApplicationState.RiskingCompleted)
    val expired: AgentApplication = inComplete
      .modify(_.applicationState)
      .setTo(ApplicationState.Expired)
      .modify(_.expiredAt)
      .setTo(Some(tdAll.nowAsInstant))
      .modify(_.postExpiryGracePeriodEndsAt)
      .setTo(Some(tdAll.nowAsInstant.plusSeconds(3600 * 24 * 45)))

  "routes should have correct paths and methods" in:
    AppRoutes.providedetails.StartController.start(linkId) shouldBe Call(
      method = "GET",
      url = path
    )

  s"GET $path should return 200 and render the start page when application is still in progress" in:
    AgentRegistrationStubs.stubFindApplicationByLinkId(linkId = linkId, agentApplication = agentApplication.inComplete)
    val response: WSResponse = get(path)
    response.status shouldBe Status.OK
    response.parseBodyAsJsoupDocument.title() shouldBe "Sign in and confirm your details - Apply for an agent services account - GOV.UK"
    AgentRegistrationStubs.verifyFindApplicationByLinkId(linkId = linkId)

  s"GET $path with complete application should return 303 and redirect to the CYA page when risking is not complete" in:
    AgentRegistrationStubs.stubFindApplicationByLinkId(linkId = linkId, agentApplication = agentApplication.complete)
    val response: WSResponse = get(path)
    response.status shouldBe Status.SEE_OTHER
    response.body[String] shouldBe ""
    response.header("Location") shouldBe Some("/agent-registration/provide-details/check-your-answers/link-id-12345")
    AgentRegistrationStubs.verifyFindApplicationByLinkId(linkId = linkId)

  s"GET $path with complete application should return 200 and render the outcome start page when risking is complete" in:
    AgentRegistrationStubs.stubFindApplicationByLinkId(linkId = linkId, agentApplication = agentApplication.riskingComplete)
    val response: WSResponse = get(path)
    response.status shouldBe Status.OK
    response.parseBodyAsJsoupDocument.title() shouldBe "Sign in to see the outcome of the application - Apply for an agent services account - GOV.UK"
    AgentRegistrationStubs.verifyFindApplicationByLinkId(linkId = linkId)

  s"GET $path with expired application should return 200 and render the expired page" in:
    AgentRegistrationStubs.stubFindApplicationByLinkId(linkId = linkId, agentApplication = agentApplication.expired)
    val response: WSResponse = get(path)
    response.status shouldBe Status.OK
    response.parseBodyAsJsoupDocument.title() shouldBe "Application has expired - Apply for an agent services account - GOV.UK"
    AgentRegistrationStubs.verifyFindApplicationByLinkId(linkId = linkId)
