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

import play.api.data.FieldMapping
import play.api.data.Form
import play.api.data.Forms
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.FormatterFactory
import uk.gov.hmrc.agentregistrationfrontend.forms.helpers.ErrorKeys

object RemoveKeyIndividualForm:

  val key: String = "removeKeyIndividual"
  def form(individualName: String): Form[YesNo] =
    val fieldMapping: FieldMapping[YesNo] = Forms.of(using
      FormatterFactory.makeEnumFormatter[YesNo](
        errorMessageIfMissing = s"$key.error.required",
        errorMessageIfEnumError = ErrorKeys.invalidInputErrorMessage(key),
        missingArgs = Seq(individualName)
      )
    )
    Form(
      mapping =
        Forms.mapping(
          key -> fieldMapping
        )(identity)(Some(_))
    )
