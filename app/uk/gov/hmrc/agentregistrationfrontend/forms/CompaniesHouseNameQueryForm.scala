/*
 * Copyright 2024 HM Revenue & Customs
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
import uk.gov.hmrc.agentregistration.shared.companieshouse.CompaniesHouseNameQuery
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.TextFormatter
import uk.gov.hmrc.agentregistrationfrontend.forms.helpers.ErrorKeys

object CompaniesHouseNameQueryForm:

  val firstNameKey: String = "firstName"
  val lastNameKey: String = "lastName"
  private val nameRegex = "^[a-zA-Z\\s\\-']+$"
  private def canonicalise(name: String): String = name.trim.replaceAll("\\s+", " ")

  val form: Form[CompaniesHouseNameQuery] = Form[CompaniesHouseNameQuery](
    mapping(
      firstNameKey -> Forms.of(using TextFormatter(ErrorKeys.requiredFieldErrorMessage(firstNameKey)))
        .transform[String](canonicalise, identity)
        .verifying(
          ErrorKeys.requiredFieldErrorMessage(firstNameKey),
          _.nonEmpty
        )
        .verifying(
          ErrorKeys.invalidInputErrorMessage(firstNameKey),
          _.matches(nameRegex)
        ),
      lastNameKey -> Forms.of(using TextFormatter(ErrorKeys.requiredFieldErrorMessage(lastNameKey)))
        .transform[String](canonicalise, identity)
        .verifying(
          ErrorKeys.requiredFieldErrorMessage(lastNameKey),
          _.nonEmpty
        )
        .verifying(
          ErrorKeys.invalidInputErrorMessage(lastNameKey),
          _.matches(nameRegex)
        )
    )(CompaniesHouseNameQuery.apply)(CompaniesHouseNameQuery.unapply)
  )
