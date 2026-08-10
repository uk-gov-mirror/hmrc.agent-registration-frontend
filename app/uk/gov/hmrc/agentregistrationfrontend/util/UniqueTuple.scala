/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentregistrationfrontend.util

import scala.annotation.nowarn
import scala.compiletime.*

opaque type UniqueTuple[T <: Tuple] <: Tuple = T

@nowarn("msg=unused.*parameter")
object UniqueTuple:

  extension [T <: Tuple](t: T)
    inline def unique: UniqueTuple[T] = UniqueTuple(t)

  inline def apply[T <: Tuple](t: T): UniqueTuple[T] =
    inline if hasDuplicates[T]
    then failDuplicateTuple[T]
    else t

  extension [Data <: Tuple](ut: UniqueTuple[Data])

    inline def tuple: Data = ut

    inline def add[A](value: A)(using A AbsentIn Data): UniqueTuple[A *: Data] = value *: ut

    inline def get[A](using A PresentIn Data): A = getImpl[Data, A](ut)

    inline def update[A](value: A)(using A PresentIn Data): UniqueTuple[Data] = updateImpl[Data, A](ut, value)

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    inline def replace[Old, New](value: New)(using
      Old PresentIn Data,
      New AbsentIn Data
    ): UniqueTuple[Replace[Old, New, Data]] = replaceImpl[Data, Old, New](ut, value).asInstanceOf[UniqueTuple[Replace[Old, New, Data]]]

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    inline def delete[A](using A PresentIn Data): UniqueTuple[Delete[A, Data]] = deleteImpl[Data, A](ut).asInstanceOf[UniqueTuple[Delete[A, Data]]]

    inline def toTuple: Data = ut

  infix trait AbsentIn[T, Tup <: Tuple]

  object AbsentIn:
    transparent inline given [T, Tup <: Tuple]: AbsentIn[T, Tup] = ${ UniqueTupleMacros.makeAbsentInOrFailImpl[T, Tup] }

  infix trait PresentIn[T, Tup <: Tuple]

  object PresentIn:
    transparent inline given [T, Tup <: Tuple]: PresentIn[T, Tup] = ${ UniqueTupleMacros.makePresentInOrFailImpl[T, Tup] }

  type Replace[
    Old,
    New,
    Tup <: Tuple
  ] <: Tuple =
    Tup match
      case Old *: tail => New *: tail
      case h *: tail => h *: Replace[Old, New, tail]
      case EmptyTuple => EmptyTuple

  type Delete[
    T,
    Tup <: Tuple
  ] <: Tuple =
    Tup match
      case T *: tail => tail
      case h *: tail => h *: Delete[T, tail]
      case EmptyTuple => EmptyTuple

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Recursion"))
  private inline def getImpl[Tup <: Tuple, E](t: Tup): E =
    inline erasedValue[Tup] match
      case _: (E *: tail) => t.asInstanceOf[E *: tail].head
      case _: (h *: tail) => getImpl[tail, E](t.asInstanceOf[h *: tail].tail)
      case _ =>
        error(
          "Cannot extract value from generic tuple. The method calling this must be 'inline' to preserve the tuple structure, or the value must be passed explicitly."
        )

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Recursion"))
  private inline def updateImpl[Tup <: Tuple, E](t: Tup, v: E): Tup =
    inline erasedValue[Tup] match
      case _: (E *: tail) =>
        val cons = t.asInstanceOf[E *: tail]
        (v *: cons.tail).asInstanceOf[Tup]
      case _: (h *: tail) =>
        val cons = t.asInstanceOf[h *: tail]
        (cons.head *: updateImpl[tail, E](cons.tail, v)).asInstanceOf[Tup]
      case _ => error("Cannot update value in generic tuple. The method calling this must be 'inline' to preserve the tuple structure.")

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Recursion"))
  private inline def replaceImpl[
    Tup <: Tuple,
    Old,
    New
  ](t: Tup, v: New): Tuple =
    inline erasedValue[Tup] match
      case _: (Old *: tail) =>
        val cons = t.asInstanceOf[Old *: tail]
        v *: cons.tail
      case _: (h *: tail) =>
        val cons = t.asInstanceOf[h *: tail]
        cons.head *: replaceImpl[tail, Old, New](cons.tail, v)
      case _ => error("Cannot replace value in generic tuple. The method calling this must be 'inline' to preserve the tuple structure.")

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Recursion"))
  private inline def deleteImpl[Tup <: Tuple, T](t: Tup): Tuple =
    inline erasedValue[Tup] match
      case _: (T *: tail) => t.asInstanceOf[T *: tail].tail
      case _: (h *: tail) =>
        val cons = t.asInstanceOf[h *: tail]
        cons.head *: deleteImpl[tail, T](cons.tail)
      case _ => error("Cannot delete value from generic tuple. The method calling this must be 'inline' to preserve the tuple structure.")

  private inline def failDuplicateTuple[T]: Nothing = ${ UniqueTupleMacros.failDuplicateTupleImpl[T] }

  private transparent inline def hasDuplicates[T <: Tuple]: Boolean = ${ UniqueTupleMacros.hasDuplicatesImpl[T] }
