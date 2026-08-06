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

import play.api.data.Forms
import uk.gov.hmrc.agentregistration.shared.lists.FiveOrLess
import uk.gov.hmrc.agentregistration.shared.lists.NumberOfRequiredKeyIndividuals
import uk.gov.hmrc.agentregistration.shared.lists.SixOrMore
import uk.gov.hmrc.agentregistrationfrontend.forms.formatters.FormatterFactory
import uk.gov.hmrc.agentregistrationfrontend.forms.helpers.ErrorKeys
import uk.gov.hmrc.agentregistrationfrontend.forms.mappings.Mappings.numberFromString
import uk.gov.voa.play.form.ConditionalMappings.isEqual
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIf

object NumberOfKeyIndividualsForm:

  import play.api.data.Form
  import play.api.data.Forms.*

  val howManyIndividualsOption: String = "numberOfKeyIndividuals"
  val howManyIndividuals: String = "exactNumberOfOfficials"
  val howManyIndividualsResponsibleForTaxMatters: String = "numberOfOfficialsWhoDealWithTax"

  enum HowManyIndividualsOption:

    case FiveOrLess
    case SixOrMore

  val form: Form[NumberOfRequiredKeyIndividuals] = Form(
    mapping = (mapping(
      howManyIndividualsOption -> Forms.of(using
        FormatterFactory.makeEnumFormatter[HowManyIndividualsOption](
          errorMessageIfMissing = ErrorKeys.requiredFieldErrorMessage(howManyIndividualsOption)
        )
      ),
      howManyIndividuals -> mandatoryIf(
        isEqual(howManyIndividualsOption, HowManyIndividualsOption.FiveOrLess.toString),
        numberFromString(howManyIndividuals)
          .transform[FiveOrLess](FiveOrLess.apply, _.numberOfKeyIndividuals)
          .verifying(
            ErrorKeys.invalidInputErrorMessage(howManyIndividuals),
            _.isValid
          )
      ),
      howManyIndividualsResponsibleForTaxMatters -> mandatoryIf(
        isEqual(howManyIndividualsOption, HowManyIndividualsOption.SixOrMore.toString),
        numberFromString(howManyIndividualsResponsibleForTaxMatters)
          .transform[SixOrMore](SixOrMore.apply, _.numberOfKeyIndividualsResponsibleForTaxMatters)
          .verifying(
            ErrorKeys.invalidInputErrorMessage(howManyIndividualsResponsibleForTaxMatters),
            _.isValid
          )
      )
    )((a, b, c) => (a, b, c))(tuple => Some(tuple)))
      .verifying(
        error = ErrorKeys.requiredFieldErrorMessage(howManyIndividualsOption),
        constraint = {
          case (HowManyIndividualsOption.FiveOrLess, Some(_), _) => true
          case (HowManyIndividualsOption.SixOrMore, _, Some(_)) => true
          case _ => false
        }
      )
      .transform[NumberOfRequiredKeyIndividuals](
        {
          case (HowManyIndividualsOption.FiveOrLess, Some(n), _) => n
          case (HowManyIndividualsOption.SixOrMore, _, Some(n)) => n
          case culprit => throw new IllegalStateException(s"Form validation failed: $culprit")
        },
        {
          case n: FiveOrLess => (HowManyIndividualsOption.FiveOrLess, Some(n), None)
          case n: SixOrMore => (HowManyIndividualsOption.SixOrMore, None, Some(n))
        }
      )
  )
