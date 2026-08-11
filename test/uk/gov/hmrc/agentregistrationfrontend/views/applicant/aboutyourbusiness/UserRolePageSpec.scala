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

package uk.gov.hmrc.agentregistrationfrontend.views.applicant.aboutyourbusiness

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uk.gov.hmrc.agentregistration.shared.UserRole
import uk.gov.hmrc.agentregistrationfrontend.forms.UserRoleForm
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ViewSpec
import uk.gov.hmrc.agentregistrationfrontend.views.html.applicant.aboutyourbusiness.UserRolePage

class UserRolePageSpec
extends ViewSpec:

  val viewTemplate: UserRolePage = app.injector.instanceOf[UserRolePage]

  final case class TestCase(
    userRole: UserRole,
    expectedRadioGroup: TestRadioGroup,
    expectedErrorMessage: String
  )

  val testCases: Seq[TestCase] = Seq(
    TestCase(
      userRole = UserRole.Owner,
      expectedRadioGroup = TestRadioGroup(
        legend = "Are you the owner of the business?",
        options = List(
          "Yes" -> UserRole.Owner.toString,
          "No, but I’m authorised by them to set up this account" -> UserRole.Authorised.toString
        ),
        hint = None
      ),
      expectedErrorMessage = "Select yes if you are the business owner"
    ),
    TestCase(
      userRole = UserRole.Director,
      expectedRadioGroup = TestRadioGroup(
        legend = "Are you a director of the limited company?",
        options = List(
          "Yes, I’m a current officer in Companies House" -> UserRole.Director.toString,
          "No, but I’m authorised by them to set up this account" -> UserRole.Authorised.toString
        ),
        hint = None
      ),
      expectedErrorMessage = "Select yes if you are a director of the limited company"
    ),
    TestCase(
      userRole = UserRole.Member,
      expectedRadioGroup = TestRadioGroup(
        legend = "Are you a member of the limited liability partnership?",
        options = List(
          "Yes, I’m a current officer in Companies House" -> UserRole.Member.toString,
          "No, but I’m authorised by them to set up this account" -> UserRole.Authorised.toString
        ),
        hint = None
      ),
      expectedErrorMessage = "Select yes if you are a member of the limited liability partnership"
    ),
    TestCase(
      userRole = UserRole.Partner,
      expectedRadioGroup = TestRadioGroup(
        legend = "Are you a partner in the business?",
        options = List(
          "Yes, I am a partner in the business" -> UserRole.Partner.toString,
          "No, but I’m authorised by them to set up this account" -> UserRole.Authorised.toString
        ),
        hint = None
      ),
      expectedErrorMessage = "Select yes if you are a partner in the business"
    )
  )

  testCases.foreach: testCase =>
    val heading = testCase.expectedRadioGroup.legend
    val form = UserRoleForm.form(testCase.userRole.toString)
    val doc: Document = Jsoup.parse(viewTemplate(form, testCase.userRole).body)
    s"UserRolePage for ${testCase.userRole.toString}" should:

      "have the correct title" in:
        doc.title() shouldBe s"$heading - Apply for an agent services account - GOV.UK"

      "render a radio button for each option" in:
        doc.mainContent.extractRadioGroup() shouldBe testCase.expectedRadioGroup

      "render a continue button" in:
        doc.select("button[type=submit]").text() shouldBe "Continue"

      "render a form error when the form contains an error" in:
        val field = "userRole"
        val errorMessage = testCase.expectedErrorMessage
        val formWithError = UserRoleForm.form(testCase.userRole.toString)
          .withError(field, errorMessage)
        behavesLikePageWithErrorHandling(
          field = field,
          errorMessage = errorMessage,
          errorDoc = Jsoup.parse(viewTemplate(formWithError, testCase.userRole).body),
          heading = heading
        )
