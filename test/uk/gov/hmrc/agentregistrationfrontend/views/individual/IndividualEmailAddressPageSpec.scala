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

import uk.gov.hmrc.agentregistrationfrontend.forms.IndividualEmailAddressForm
import uk.gov.hmrc.agentregistrationfrontend.model.SubmitAction.SaveAndContinue
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ViewSpec
import uk.gov.hmrc.agentregistrationfrontend.views.html.individual.IndividualEmailAddressPage

class IndividualEmailAddressPageSpec
extends ViewSpec:

  private val viewTemplate: IndividualEmailAddressPage = app.injector.instanceOf[IndividualEmailAddressPage]
  private val linkId = tdAll.linkId

  val doc: Document = Jsoup.parse(viewTemplate(IndividualEmailAddressForm.form, linkId).body)
  private val heading: String = "What is your email address?"

  "MemberEmailAddressPage" should:

    "have the correct title" in:
      doc.title() shouldBe s"$heading - Apply for an agent services account - GOV.UK"

    "render a form with an input of type email for email address" in:
      val form = doc.mainContent.selectOrFail("form").selectOnlyOneElementOrFail()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe AppRoutes.providedetails.IndividualEmailAddressController.submit(linkId).url
      form
        .selectOrFail("label[for=individualEmailAddress]")
        .selectOnlyOneElementOrFail()
        .text() shouldBe heading
      form
        .selectOrFail("input[name=individualEmailAddress][type=email]")
        .selectOnlyOneElementOrFail()

    "render a save and continue button" in:
      doc
        .mainContent
        .selectOrFail(s"form button[value='${SaveAndContinue.toString}']")
        .selectOnlyOneElementOrFail()
        .text() shouldBe "Save and continue"

    "render the form error correctly when the form contains an error" in:
      val field = IndividualEmailAddressForm.key
      val errorMessage = "Enter your email address"
      val formWithError = IndividualEmailAddressForm.form
        .withError(field, errorMessage)
      behavesLikePageWithErrorHandling(
        field = field,
        errorMessage = errorMessage,
        errorDoc = Jsoup.parse(viewTemplate(formWithError, linkId).body),
        heading = heading
      )
