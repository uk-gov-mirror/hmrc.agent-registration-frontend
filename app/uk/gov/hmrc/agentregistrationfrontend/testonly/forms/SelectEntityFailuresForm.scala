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

package uk.gov.hmrc.agentregistrationfrontend.testonly.forms

import play.api.data.Form
import play.api.data.Forms.of
import play.api.data.Forms.seq
import play.api.data.Forms.single
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.BusinessType
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.=!=
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.FormatterFactory
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.EntityRiskingFailure

object SelectEntityFailuresForm:

  val key: String = "failures"

  /** Checks 4.2 and 5.2 are Corporation Tax specific (overdue CoTax returns/liabilities) and so can never genuinely apply to a sole trader, who has no CoTax
    * obligations. Also used by `TestOnlyController`'s random quick-action pickers, so a sole trader application can never roll these either.
    */
  val soleTraderInapplicableFailures: Set[EntityRiskingFailure] = Set(EntityRiskingFailure.Check_4_2, EntityRiskingFailure.Check_5_2)

  def apply(agentApplication: AgentApplication): Form[Seq[EntityRiskingFailure]] = Form(
    single(
      key -> seq(of(using FormatterFactory.makeEnumFormatter[EntityRiskingFailure]()))
        .verifying(
          "Only one AMLS (Check 3) failure can be selected at a time.",
          failures => failures.count(_.checkId === "3") <= 1
        )
        .verifying(
          "Checks 4.2 and 5.2 (Corporation Tax) cannot be selected for a sole trader application.",
          failures => agentApplication.businessType =!= BusinessType.SoleTrader || !failures.exists(soleTraderInapplicableFailures.contains)
        )
    )
  )
