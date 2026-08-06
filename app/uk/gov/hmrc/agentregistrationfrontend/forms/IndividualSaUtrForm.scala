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

package uk.gov.hmrc.agentregistrationfrontend.forms

import play.api.data.Form
import play.api.data.Forms
import play.api.data.Forms.mapping
import play.api.data.Forms.text
import uk.gov.hmrc.agentregistration.shared.SaUtr
import uk.gov.hmrc.agentregistration.shared.individual.IndividualSaUtr
import uk.gov.hmrc.agentregistration.shared.individual.UserProvidedSaUtr
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.TextFormatter
import uk.gov.hmrc.agentregistrationfrontend.forms.helpers.ErrorKeys
import uk.gov.voa.play.form.ConditionalMappings.isEqual
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIf

object IndividualSaUtrForm:

  private val yes: String = YesNo.Yes.toString
  private val no: String = YesNo.No.toString

  val hasSaUtrKey = "individualSaUtr.hasSaUtr"
  val saUtrKey = "individualSaUtr.saUtr"

  val form: Form[UserProvidedSaUtr] = Form(
    mapping(
      hasSaUtrKey -> Forms.of(using TextFormatter(ErrorKeys.requiredFieldErrorMessage(hasSaUtrKey)))
        .verifying(
          ErrorKeys.requiredFieldErrorMessage(hasSaUtrKey),
          _.nonEmpty
        ),
      saUtrKey -> mandatoryIf(
        isEqual(hasSaUtrKey, yes),
        text
          .verifying(
            ErrorKeys.requiredFieldErrorMessage(saUtrKey),
            _.nonEmpty
          )
          .verifying(
            ErrorKeys.invalidInputErrorMessage(saUtrKey),
            value => SaUtr.isValid(value)
          )
      )
    )(
      (
        hasSaUtrStr,
        saUtrStrOpt
      ) =>
        (hasSaUtrStr, saUtrStrOpt) match
          case (`yes`, Some(saUtrStr)) => IndividualSaUtr.Provided(SaUtr(saUtrStr))
          case _ => IndividualSaUtr.NotProvided
    ) {
      case IndividualSaUtr.Provided(saUtr) => Some((yes, Some(saUtr.value)))
      case IndividualSaUtr.NotProvided => Some((no, None))
    }
  )
