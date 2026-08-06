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

package uk.gov.hmrc.agentregistrationfrontend.testsupport.viewspecsupport

import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.scalactic.source.Position
import uk.gov.hmrc.agentregistrationfrontend.testsupport.RichMatchers.*
import uk.gov.hmrc.agentregistrationfrontend.testsupport.viewspecsupport.JsoupHelper.*

import scala.language.implicitConversions
import scala.annotation.nowarn

object ElementSupport:

  extension (element: Element)

    inline def selectOrFail(selector: String)(using pos: Position): Elements =
      @nowarn
      val positionInfo = s"at (${pos.fileName}:${pos.lineNumber})"
      val elements: Elements = element.select(selector)
      if elements.isEmpty then
        fail(s"Selector '$selector' didn't yield any results $hints")
      else
        elements

    inline def selectAttrOrFail(attributeKey: String)(using pos: Position): String =
      if element.hasAttr(attributeKey) then
        element.attr(attributeKey)
      else
        fail(s"Attribute '$attributeKey' not found $hints")

    inline def selectShouldFail(selector: String)(using pos: Position): Unit =
      val selectedElements: Elements = element.select(selector)
      if (!selectedElements.isEmpty)
        fail(s"selector '$selector' yielded results when it should have failed $hints")
      else
        ()

    private def elementHint = s"""\n\nElement text was:\n${element.wholeText().stripSpaces}\n\nElement content was:\n${element.toString}\n"""
    private def hints(using pos: Position): String = s"$positionInfo$elementHint"
