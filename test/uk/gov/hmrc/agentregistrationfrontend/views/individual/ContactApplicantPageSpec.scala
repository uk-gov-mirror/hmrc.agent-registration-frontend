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
import uk.gov.hmrc.agentregistrationfrontend.views.html.individual.ContactApplicantPage

class ContactApplicantPageSpec
extends ViewSpec:

  private val viewTemplate: ContactApplicantPage = app.injector.instanceOf[ContactApplicantPage]

  private val doc: Document = Jsoup.parse(viewTemplate().body)
  private val heading: String = "Name matching has failed contact the applicant"

  "ContactApplicant page" should:
    "have the correct title" in:
      doc.title() shouldBe s"$heading - Apply for an agent services account - GOV.UK"

    "have the correct content" in:
      doc.mainContent shouldContainContent
        """
          |We have been unable to match your name to the name on the application. Contact the applicant.
          |""".stripMargin
