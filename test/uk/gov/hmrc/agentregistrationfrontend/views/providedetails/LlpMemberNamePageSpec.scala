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

package uk.gov.hmrc.agentregistrationfrontend.views.providedetails

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uk.gov.hmrc.agentregistrationfrontend.controllers.providedetails.routes
import uk.gov.hmrc.agentregistrationfrontend.forms.CompaniesHouseNameQueryForm
import uk.gov.hmrc.agentregistrationfrontend.model.SubmitAction.SaveAndComeBackLater
import uk.gov.hmrc.agentregistrationfrontend.model.SubmitAction.SaveAndContinue
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ViewSpec
import uk.gov.hmrc.agentregistrationfrontend.views.html.providedetails.LlpMemberNamePage

class LlpMemberNamePageSpec
extends ViewSpec:

  val viewTemplate: LlpMemberNamePage = app.injector.instanceOf[LlpMemberNamePage]
  val doc: Document = Jsoup.parse(viewTemplate(CompaniesHouseNameQueryForm.form).body)
  private val heading: String = "What is your name?"

  "MemberNamePage" should:

    "have the correct title" in:
      doc.title() shouldBe s"$heading - Apply for an agent services account - GOV.UK"

    "render an introductory paragraph using the company name from business details" in:
      doc
        .mainContent
        .selectOrFail("p.govuk-body")
        .selectOnlyOneElementOrFail()
        .text() shouldBe "We’ll check this against the business records for Test Company Name on Companies House."

    "render a form with inputs for first and last name" in:
      val form = doc.mainContent.selectOrFail("form").selectOnlyOneElementOrFail()
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe routes.LlpMemberNameController.submit.url
      form
        .selectOrFail("label[for=firstName]")
        .selectOnlyOneElementOrFail()
        .text() shouldBe "First names"
      form
        .selectOrFail("input[name=firstName][type=text]")
        .selectOnlyOneElementOrFail()
      form
        .selectOrFail("label[for=lastName]")
        .selectOnlyOneElementOrFail()
        .text() shouldBe "Last name"
      form
        .selectOrFail("input[name=lastName][type=text]")
        .selectOnlyOneElementOrFail()

    "render a save and continue button" in:
      doc
        .mainContent
        .selectOrFail(s"form button[value='${SaveAndContinue.toString}']")
        .selectOnlyOneElementOrFail()
        .text() shouldBe "Save and continue"

    "render a save and come back later button" in:
      doc
        .mainContent
        .selectOrFail(s"form button[value=${SaveAndComeBackLater.toString}]")
        .selectOnlyOneElementOrFail()
        .text() shouldBe "Save and come back later"

    "render a form error when the form contains an error on the first name" in:
      val field = CompaniesHouseNameQueryForm.firstNameKey
      val errorMessage = "Enter a first name"
      val formWithError = CompaniesHouseNameQueryForm.form
        .withError(field, errorMessage)
      behavesLikePageWithErrorHandling(
        field = field,
        errorMessage = errorMessage,
        errorDoc = Jsoup.parse(viewTemplate(formWithError).body),
        heading = heading
      )

    "render a form error when the form contains an error on the last name" in:
      val field = CompaniesHouseNameQueryForm.lastNameKey
      val errorMessage = "Enter a last name"
      val formWithError = CompaniesHouseNameQueryForm.form
        .withError(field, errorMessage)
      behavesLikePageWithErrorHandling(
        field = field,
        errorMessage = errorMessage,
        errorDoc = Jsoup.parse(viewTemplate(formWithError).body),
        heading = heading
      )

    "render multiple form errors when the form contains errors on both first and last name" in:
      val firstNameField = CompaniesHouseNameQueryForm.firstNameKey
      val firstNameErrorMessage = "Enter your first name"
      val lastNameField = CompaniesHouseNameQueryForm.lastNameKey
      val lastNameErrorMessage = "Enter your last name"
      val formWithErrors = CompaniesHouseNameQueryForm.form
        .withError(firstNameField, firstNameErrorMessage)
        .withError(lastNameField, lastNameErrorMessage)
      val errorDoc = Jsoup.parse(viewTemplate(formWithErrors).body)
      val summaryLinkForFirstName = errorDoc.select(errorSummaryLink).select(s"a[href='#${firstNameField}']").selectOnlyOneElementOrFail()
      val summaryLinkForLastName = errorDoc.select(errorSummaryLink).select(s"a[href='#${lastNameField}']").selectOnlyOneElementOrFail()
      val inlineErrorForFirstName = errorDoc.select(s"p#$firstNameField-error").selectOnlyOneElementOrFail()
      val inlineErrorForLastName = errorDoc.select(s"p#$lastNameField-error").selectOnlyOneElementOrFail()
      errorDoc.title() shouldBe s"Error: $heading - Apply for an agent services account - GOV.UK"
      errorDoc.selectOrFail(".govuk-error-summary__title").selectOnlyOneElementOrFail().text() shouldBe "There is a problem"
      summaryLinkForFirstName.text() shouldBe firstNameErrorMessage
      inlineErrorForFirstName.text() shouldBe s"Error: $firstNameErrorMessage"
      summaryLinkForLastName.text() shouldBe lastNameErrorMessage
      inlineErrorForLastName.text() shouldBe s"Error: $lastNameErrorMessage"
