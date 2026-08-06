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

package uk.gov.hmrc.agentregistrationfrontend.forms.mappings

import play.api.data.Forms.of
import play.api.data.Mapping
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.LocalDateFormatter
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.TextFormatter
import uk.gov.hmrc.agentregistrationfrontend.forms.helpers.ErrorKeys
import uk.gov.hmrc.agentregistrationfrontend.forms.helpers.FormFieldHelper

import java.time.LocalDate

object Mappings:

  def text(formMessageKey: String): Mapping[String] = of(using
    TextFormatter(
      errorMessageIfMissing = ErrorKeys.requiredFieldErrorMessage(formMessageKey)
    )
  )

  def localDate(formMessageKey: String): Mapping[LocalDate] = of(using LocalDateFormatter(formMessageKey))

  def textFromOptions(
    formMessageKey: String,
    options: Seq[String]
  ): Mapping[String] = text(formMessageKey = formMessageKey)
    .verifying(
      FormFieldHelper.constraint(
        constraint = options.contains,
        error = ErrorKeys.invalidInputErrorMessage(formMessageKey)
      )
    )

  def numberFromString(formMessageKey: String): Mapping[Int] = text(formMessageKey)
    .verifying(
      ErrorKeys.requiredFieldErrorMessage(formMessageKey),
      _.nonEmpty
    )
    .verifying(
      ErrorKeys.invalidInputErrorMessage(formMessageKey),
      str => str.toIntOption.isDefined
    )
    .transform[Int](_.toInt, _.toString)
