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

package uk.gov.hmrc.agentregistrationfrontend.forms.applicant.fixablefailures

import play.api.data.FieldMapping
import play.api.data.Form
import play.api.data.Forms
import play.api.data.Forms.mapping
import uk.gov.hmrc.agentregistrationfrontend.forms.YesNo
import uk.gov.hmrc.agentregistrationfrontend.forms.YesNo.toYesNo
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.FormatterFactory
import uk.gov.hmrc.agentregistrationfrontend.forms.helpers.ErrorKeys

object ConfirmFixForm:

  val key: String = "isFixed"

  def form(failureCode: String): Form[Boolean] =
    val yesNoMapping: FieldMapping[YesNo] = Forms.of(using
      FormatterFactory.makeEnumFormatter[YesNo](
        errorMessageIfMissing = ErrorKeys.requiredFieldErrorMessage(s"$key.$failureCode"),
        errorMessageIfEnumError = ErrorKeys.invalidInputErrorMessage(s"$key.$failureCode")
      )
    )
    Form(
      mapping(
        key -> yesNoMapping
      )(identity)(Some(_))
        .transform[Boolean](
          _.toBoolean,
          _.toYesNo
        )
    )
