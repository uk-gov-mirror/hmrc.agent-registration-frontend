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
import uk.gov.hmrc.agentregistration.shared.agentdetails.AgentTelephoneNumber
import uk.gov.hmrc.agentregistration.shared.util.StringExtensions.canonicalise
import uk.gov.hmrc.agentregistration.shared.util.StringExtensions.stripAllWhiteSpace
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.TextFormatter
import uk.gov.hmrc.agentregistrationfrontend.forms.helpers.ErrorKeys
import uk.gov.voa.play.form.ConditionalMappings.isEqual
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIf

object AgentTelephoneNumberForm:

  val key: String = "agentTelephoneNumber"
  val otherKey: String = "otherAgentTelephoneNumber"

  val form: Form[AgentTelephoneNumber] = Form(
    mapping =
      mapping(
        key -> Forms.of(using TextFormatter(ErrorKeys.requiredFieldErrorMessage(key)))
          .verifying(
            ErrorKeys.requiredFieldErrorMessage(key),
            _.nonEmpty
          ),
        otherKey -> mandatoryIf(
          isEqual(key, "other"),
          text
            .transform[String](canonicalise, identity)
            .verifying(
              ErrorKeys.requiredFieldErrorMessage(otherKey),
              _.nonEmpty
            )
            .verifying(
              ErrorKeys.inputTooLongErrorMessage(otherKey),
              _.stripAllWhiteSpace.length <= 24
            )
            .verifying(
              ErrorKeys.invalidInputErrorMessage(otherKey),
              value => AgentTelephoneNumber.isValid(value.stripAllWhiteSpace)
            )
        )
      )(AgentTelephoneNumber.apply)(a => Some((a.agentTelephoneNumber, a.otherAgentTelephoneNumber)))
  )
