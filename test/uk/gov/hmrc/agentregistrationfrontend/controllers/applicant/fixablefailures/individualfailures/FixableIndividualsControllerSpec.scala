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

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.fixablefailures.individualfailures

import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentregistration.shared.AgentApplicationLlp
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.lists.IndividualName
import uk.gov.hmrc.agentregistration.shared.risking.IndividualFix
import uk.gov.hmrc.agentregistration.shared.risking.RiskingOutcomeIndividual
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.ApplyStubHelper
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ControllerSpec

class FixableIndividualsControllerSpec
extends ControllerSpec:

  override def configOverrides: Map[String, Any] =
    super.configOverrides ++ Map(
      "features.fixable-failures" -> true
    )

  private val path = "/agent-registration/conditions-not-yet-met/individuals"
  object agentApplication:
    val riskingCompletedFixable: AgentApplicationLlp =
      tdAll
        .agentApplicationLlp
        .afterRiskingCompletedFixable

  object individualProvidedDetails:
    val afterRiskedFixable: IndividualProvidedDetails =
      tdAll
        .providedDetails
        .afterRiskedFixable

  "route should have correct path and method" in:
    AppRoutes.fixablefailures.individualfailures.FixableIndividualsController.show shouldBe Call(
      method = "GET",
      url = path
    )

  s"GET $path for fixable individuals should render page" in:

    ApplyStubHelper.stubsForApplicationBprAndIndividuals(
      application = agentApplication.riskingCompletedFixable,
      individuals = List(
        individualProvidedDetails.afterRiskedFixable,
        individualProvidedDetails.afterRiskedFixable.copy(
          individualName = IndividualName("Test Name 2"),
          riskingOutcomeIndividual = Some(RiskingOutcomeIndividual.FailedFixable(
            fixes = Seq(
              IndividualFix._5._1(isConfirmed = None),
              IndividualFix._8._7(isConfirmed = None)
            ),
            declarationAgreed = false
          ))
        ),
        individualProvidedDetails.afterRiskedFixable.copy(
          individualName = IndividualName("Test Name 3"),
          riskingOutcomeIndividual = Some(RiskingOutcomeIndividual.FailedFixable(
            fixes = Seq(
              IndividualFix._8._7(isConfirmed = Some(true)),
              IndividualFix._10.IndividualDetailsFix(
                dateOfBirth = Some(tdAll.dateOfBirthProvided),
                saUtr = None,
                nino = None,
                isConfirmed = Some(true)
              )
            ),
            declarationAgreed = true
          ))
        )
      )
    )

    val response: WSResponse = get(path)

    val testTable = TestTable(
      caption = "Actions to be completed",
      rows = List(
        IndexedSeq(
          "Test Name",
          "File one or more relevant returns",
          "No"
        ),
        IndexedSeq(
          "Test Name 2",
          "Pay one or more overdue liabilities Pay a liability connected to relevant anti-avoidance",
          "No"
        ),
        IndexedSeq(
          "Test Name 3",
          "Pay a liability connected to relevant anti-avoidance Check and update their details",
          "Yes"
        )
      )
    )

    response.status shouldBe Status.OK
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "We need to hear from these people before you submit the application again - Apply for an agent services account - GOV.UK"
    doc.extractTable(
      index = 1,
      numberOfCols = 3
    ) shouldBe testTable
    ApplyStubHelper.verifyConnectorsForApplicationBprAndIndividuals(agentApplication.riskingCompletedFixable)

  s"GET $path for fixable individuals shouldn't show individuals if details were provided by applicant" in:

    ApplyStubHelper.stubsForApplicationBprAndIndividuals(
      application = agentApplication.riskingCompletedFixable,
      individuals = List(
        individualProvidedDetails.afterRiskedFixable,
        individualProvidedDetails.afterRiskedFixable.copy(
          individualName = IndividualName("Test Name 2"),
          riskingOutcomeIndividual = Some(RiskingOutcomeIndividual.FailedFixable(
            fixes = Seq(
              IndividualFix._5._1(isConfirmed = None),
              IndividualFix._8._7(isConfirmed = None)
            ),
            declarationAgreed = false
          )),
          providedByApplicant = Some(true)
        )
      )
    )

    val response: WSResponse = get(path)

    val testTable = TestTable(
      caption = "Actions to be completed",
      rows = List(
        IndexedSeq(
          "Test Name",
          "File one or more relevant returns",
          "No"
        )
      )
    )

    response.status shouldBe Status.OK
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "We need to hear from these people before you submit the application again - Apply for an agent services account - GOV.UK"
    val expectedTable = doc.extractTable(
      index = 1,
      numberOfCols = 3
    )
    expectedTable shouldBe testTable
    expectedTable.rows.contains(IndexedSeq(
      "Test Name 2",
      "Pay one or more overdue liabilities Pay a liability connected to relevant anti-avoidance",
      "No"
    )) shouldBe false withClue "sanity check"
    ApplyStubHelper.verifyConnectorsForApplicationBprAndIndividuals(agentApplication.riskingCompletedFixable)
