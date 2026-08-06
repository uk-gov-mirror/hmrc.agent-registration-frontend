/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.aboutyourbusiness

import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ControllerSpec

class NonUkAgentControllerSpec
extends ControllerSpec:

  private val path = "/agent-registration/apply/about-your-business/non-uk-agent"

  "routes should have correct paths and methods" in:
    AppRoutes.apply.aboutyourbusiness.NonUkAgentController.show shouldBe Call(
      method = "GET",
      url = path
    )

  s"GET $path before completing all other tasks should redirect to the tasklist" in:
    val response: WSResponse = get(path)

    response.status shouldBe Status.OK
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "You need to apply in a different way if the business is based outside the UK - Apply for an agent services account - GOV.UK"
