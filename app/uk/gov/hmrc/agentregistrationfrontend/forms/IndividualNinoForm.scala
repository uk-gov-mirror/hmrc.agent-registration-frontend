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
import uk.gov.voa.play.form.ConditionalMappings.isEqual
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIf
import uk.gov.hmrc.agentregistration.shared.Nino
import uk.gov.hmrc.agentregistration.shared.individual.UserProvidedNino
import uk.gov.hmrc.agentregistration.shared.individual.IndividualNino
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.TextFormatter
import uk.gov.hmrc.agentregistrationfrontend.forms.helpers.ErrorKeys

object IndividualNinoForm:

  private val yes: String = YesNo.Yes.toString
  private val no: String = YesNo.No.toString

  val hasNinoKey = "individualNino.hasNino"
  val ninoKey = "individualNino.nino"

  val form: Form[UserProvidedNino] = Form(
    mapping(
      hasNinoKey -> Forms.of(using TextFormatter(ErrorKeys.requiredFieldErrorMessage(hasNinoKey)))
        .verifying(
          ErrorKeys.requiredFieldErrorMessage(hasNinoKey),
          _.nonEmpty
        ),
      ninoKey -> mandatoryIf(
        isEqual(hasNinoKey, yes),
        text
          .verifying(
            ErrorKeys.requiredFieldErrorMessage(ninoKey),
            _.nonEmpty
          )
          .verifying(
            ErrorKeys.invalidInputErrorMessage(ninoKey),
            value => Nino.isValid(value)
          )
      )
    )(
      (
        hasNinoStr,
        ninoStrOpt
      ) =>
        (hasNinoStr, ninoStrOpt) match
          case (`yes`, Some(ninoStr)) => IndividualNino.Provided(Nino(ninoStr))
          case _ => IndividualNino.NotProvided
    ) {
      case IndividualNino.Provided(nino) => Some((yes, Some(nino.value)))
      case IndividualNino.NotProvided => Some((no, None))
    }
  )
