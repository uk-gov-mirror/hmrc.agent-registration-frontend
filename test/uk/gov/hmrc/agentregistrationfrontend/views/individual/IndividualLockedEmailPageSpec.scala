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

package uk.gov.hmrc.agentregistrationfrontend.views.individual

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import uk.gov.hmrc.agentregistrationfrontend.testsupport.ViewSpec
import uk.gov.hmrc.agentregistrationfrontend.views.html.individual.IndividualEmailLockedPage

class IndividualLockedEmailPageSpec
extends ViewSpec:

  val individualLockedEmailPage: IndividualEmailLockedPage = app.injector.instanceOf[IndividualEmailLockedPage]
  private val linkId = tdAll.linkId

  val doc: Document = Jsoup.parse(
    individualLockedEmailPage(linkId).body
  )

  "MemberEmailLockedPage" should:

    "have expected content" in:
      doc.mainContent shouldContainContent
        """
          |We cannot check your identity because you entered an incorrect verification code too many times.
          |The verification code was emailed to you.
          |What to do next
          |You can try again in 24 hours.
          |If you want to try again with a different email address you can change the email address you entered.
          |"""
          .stripMargin

    "have the correct title" in:
      doc.title() shouldBe "We could not confirm your identity - Apply for an agent services account - GOV.UK"

    "have the correct h1" in:
      doc.h1 shouldBe "We could not confirm your identity"

    "have the correct h2" in:
      doc.h2 shouldBe "What to do next"
