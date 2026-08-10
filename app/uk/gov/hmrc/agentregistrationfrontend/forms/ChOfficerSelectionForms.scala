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
import play.api.data.Mapping
import uk.gov.hmrc.agentregistration.shared.companieshouse.CompaniesHouseOfficer
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.FormatterFactory
import uk.gov.hmrc.agentregistrationfrontend.forms.helpers.ErrorKeys
import uk.gov.hmrc.agentregistrationfrontend.forms.mappings.Mappings
import uk.gov.hmrc.agentregistrationfrontend.util.Errors

final case class OfficerSelection(value: String)

/** Forms for handling Companies House (Ch) Officer Selection.
  */
object ChOfficerSelectionForms:

  val formType: Form[ChOfficerSelectionFormType] = Form(
    mapping =
      Forms.mapping(
        ChOfficerSelectionFormType.key -> Forms.of(using FormatterFactory.makeEnumFormatter[ChOfficerSelectionFormType]())
      )(identity)(Some(_))
  )

  val key: String = "companiesHouseOfficer"

  val yesNoForm: Form[YesNo] =
    val fieldMapping: FieldMapping[YesNo] = Forms.of(using
      FormatterFactory.makeEnumFormatter[YesNo](
        errorMessageIfMissing = s"$key.single.error.required",
        errorMessageIfEnumError = ErrorKeys.invalidInputErrorMessage(ChOfficerSelectionForms.key)
      )
    )
    Form(
      mapping =
        Forms.mapping(
          ChOfficerSelectionForms.key -> fieldMapping
        )(identity)(Some(_))
    )

  def officerSelectionForm(officers: Seq[CompaniesHouseOfficer]): Form[OfficerSelection] =
    Errors.require(officers.size > 1, s"This form is suitable for multiple officers: ${officers.size}")
    val mapping: Mapping[OfficerSelection] = Mappings.textFromOptions(
      formMessageKey = ChOfficerSelectionForms.key,
      options = officers.map(_.toOfficerSelection.value)
    ).transform[OfficerSelection](OfficerSelection.apply, _.value)
    Form(Forms.single(ChOfficerSelectionForms.key -> mapping))

  extension (officer: CompaniesHouseOfficer)
    def toOfficerSelection: OfficerSelection = OfficerSelection(
      s"${officer.name}|${officer.dateOfBirth.map(dob => s"${dob.day.getOrElse("")}/${dob.month}/${dob.year}").getOrElse("")}"
    )
