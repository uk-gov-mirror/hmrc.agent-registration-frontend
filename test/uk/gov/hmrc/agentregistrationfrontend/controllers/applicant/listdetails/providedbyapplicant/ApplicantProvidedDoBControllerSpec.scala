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

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.listdetails.providedbyapplicant

import play.api.libs.ws.DefaultBodyReadables.*
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.individual.IndividualDateOfBirth.ApplicantProvided
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.ApplyStubHelper
import uk.gov.hmrc.agentregistrationfrontend.forms.applicant.providedbyapplicant.ApplicantProvidedDoBForm
import uk.gov.hmrc.agentregistrationfrontend.model.ProvidedByApplicant
import uk.gov.hmrc.agentregistrationfrontend.repository.ProvidedByApplicantSessionStore
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ControllerSpec
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.AgentRegistrationStubs
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.AuthStubs

class ApplicantProvidedDoBControllerSpec
extends ControllerSpec:

  val application: AgentApplication = tdAll.agentApplicationLimitedPartnership.afterStarted

  val name: String = tdAll.providedDetails.afterAccessConfirmed.individualName.value

  val providedByApplicant: ProvidedByApplicant = ProvidedByApplicant(
    individualProvidedDetailsId = tdAll.providedDetails.afterAccessConfirmed._id,
    individualName = tdAll.providedDetails.afterAccessConfirmed.individualName
  )

  val providedByApplicantSessionStore: ProvidedByApplicantSessionStore = app.injector.instanceOf[ProvidedByApplicantSessionStore]

  override def afterEach(): Unit =
    providedByApplicantSessionStore.delete().futureValue
    super.afterEach()

  private val path = s"/agent-registration/apply/list-details/provide-details/date-of-birth"

  "routes should have correct paths and methods" in:
    AppRoutes.apply.listdetails.providedbyapplicant.ApplicantProvidedDoBController.show shouldBe Call(
      method = "GET",
      url = path
    )
    AppRoutes.apply.listdetails.providedbyapplicant.ApplicantProvidedDoBController.submit shouldBe Call(
      method = "POST",
      url = path
    )
    AppRoutes.apply.listdetails.providedbyapplicant.ApplicantProvidedDoBController.submit.url shouldBe AppRoutes
      .apply.listdetails.providedbyapplicant.ApplicantProvidedDoBController.show.url

  s"GET $path should return 200 and render page" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(application)
    providedByApplicantSessionStore.upsert(providedByApplicant).futureValue
    val response: WSResponse = get(path)

    response.status shouldBe Status.OK
    response.parseBodyAsJsoupDocument.title() shouldBe s"What is $name’s date of birth? - Apply for an agent services account - GOV.UK"

  s"GET $path should return 303 and redirect to the select individual page when there is no session data" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(application)
    providedByApplicantSessionStore.find().futureValue shouldBe None withClue "ProvidedByApplicant session store should be empty for this scenario"

    val response: WSResponse = get(path)

    response.status shouldBe Status.SEE_OTHER
    response.header("Location").value shouldBe AppRoutes.apply.listdetails.providedbyapplicant.SelectIndividualController.show.url

  s"POST $path with valid date of birth should save data and redirect to the CYA controller for navigation" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(application)
    providedByApplicantSessionStore.upsert(providedByApplicant).futureValue
    val response: WSResponse =
      post(path)(Map(
        ApplicantProvidedDoBForm.dayKey -> Seq(tdAll.dateOfBirth.getDayOfMonth.toString),
        ApplicantProvidedDoBForm.monthKey -> Seq(tdAll.dateOfBirth.getMonthValue.toString),
        ApplicantProvidedDoBForm.yearKey -> Seq(tdAll.dateOfBirth.getYear.toString)
      ))

    response.status shouldBe Status.SEE_OTHER
    response.body[String] shouldBe ""
    response.header("Location").value shouldBe AppRoutes.apply.listdetails.providedbyapplicant.CheckYourAnswersController.show.url
    ApplyStubHelper.verifyConnectorsForAuthAction()

    providedByApplicantSessionStore.find().futureValue.value shouldBe
      ProvidedByApplicant(
        individualProvidedDetailsId = tdAll.providedDetails.afterAccessConfirmed._id,
        individualName = tdAll.providedDetails.afterAccessConfirmed.individualName,
        individualDateOfBirth = Some(ApplicantProvided(tdAll.dateOfBirth))
      )

  s"POST $path without providing a date should return 400" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(application)
    providedByApplicantSessionStore.upsert(providedByApplicant).futureValue
    val response: WSResponse =
      post(path)(
        Map(
          ApplicantProvidedDoBForm.dayKey -> Seq(Constants.EMPTY_STRING),
          ApplicantProvidedDoBForm.monthKey -> Seq(Constants.EMPTY_STRING),
          ApplicantProvidedDoBForm.yearKey -> Seq(Constants.EMPTY_STRING)
        )
      )

    response.status shouldBe Status.BAD_REQUEST
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe s"Error: What is $name’s date of birth? - Apply for an agent services account - GOV.UK"
    doc.mainContent.getElementById(s"${ApplicantProvidedDoBForm.key}-error").text() shouldBe "Error: What is the date of birth?"

  s"POST $path without a valid date should return 400" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(application)
    providedByApplicantSessionStore.upsert(providedByApplicant).futureValue
    val response: WSResponse =
      post(path)(
        Map(
          ApplicantProvidedDoBForm.dayKey -> Seq("/"),
          ApplicantProvidedDoBForm.monthKey -> Seq("/"),
          ApplicantProvidedDoBForm.yearKey -> Seq("/")
        )
      )

    response.status shouldBe Status.BAD_REQUEST
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe s"Error: What is $name’s date of birth? - Apply for an agent services account - GOV.UK"
    doc.mainContent.getElementById(s"${ApplicantProvidedDoBForm.key}-error").text() shouldBe "Error: The date of birth must be a real date"
