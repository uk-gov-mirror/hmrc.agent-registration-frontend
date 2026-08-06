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

package uk.gov.hmrc.agentregistrationfrontend.forms.formatters

import play.api.data.FormError
import play.api.data.format.Formatter
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistration.shared.util.EnumValues
import uk.gov.hmrc.agentregistration.shared.util.SealedObjects

import scala.reflect.ClassTag

object FormatterFactory:

  inline def makeEnumFormatter[E <: reflect.Enum](
    errorMessageIfMissing: String = "error.required",
    errorMessageIfEnumError: String = "invalid input",
    missingArgs: => Seq[Any] = Nil,
    enumErrorArgs: => Seq[Any] = Nil
  )(using classTag: ClassTag[E]): Formatter[E] = makeFormatter[E](
    errorMessageIfMissing,
    errorMessageIfEnumError,
    EnumValues.all[E],
    () => missingArgs,
    () => enumErrorArgs
  )

  inline def makeSealedObjectsFormatter[E](
    errorMessageIfMissing: String = "error.required",
    errorMessageIfEnumError: String = "invalid input",
    missingArgs: => Seq[Any] = Nil,
    enumErrorArgs: => Seq[Any] = Nil
  )(using classTag: ClassTag[E]): Formatter[E] = makeFormatter[E](
    errorMessageIfMissing,
    errorMessageIfEnumError,
    SealedObjects.all[E],
    () => missingArgs,
    () => enumErrorArgs
  )

  private[formatters] def makeFormatter[E](
    errorMessageIfMissing: String,
    errorMessageIfEnumError: String,
    values: Seq[E],
    missingArgs: () => Seq[Any],
    enumErrorArgs: () => Seq[Any]
  ): Formatter[E] =
    new Formatter[E] {
      override def bind(
        key: String,
        data: Map[String, String]
      ): Either[Seq[FormError], E] = data.get(key)
        .toRight(Seq(FormError(
          key,
          errorMessageIfMissing,
          missingArgs()
        )))
        .flatMap { str =>
          values.find(e => e.toString === str)
            .toRight(Seq(FormError(
              key,
              errorMessageIfEnumError,
              enumErrorArgs()
            )))
        }

      override def unbind(
        key: String,
        value: E
      ): Map[String, String] = Map(key -> value.toString)
    }
