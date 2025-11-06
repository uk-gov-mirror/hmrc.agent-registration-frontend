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

package uk.gov.hmrc.agentregistrationfrontend.controllers.providedetails

import com.softwaremill.quicklens.modify
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.agentregistration.shared.contactdetails.CompaniesHouseNameQuery
import uk.gov.hmrc.agentregistration.shared.llp.CompaniesHouseMatch
import uk.gov.hmrc.agentregistrationfrontend.action.Actions
import uk.gov.hmrc.agentregistrationfrontend.action.FormValue
import uk.gov.hmrc.agentregistrationfrontend.action.providedetails.llp.MemberProvideDetailsRequest
import uk.gov.hmrc.agentregistrationfrontend.controllers.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.forms.CompaniesHouseNameQueryForm
import uk.gov.hmrc.agentregistrationfrontend.services.llp.MemberProvideDetailsService
import uk.gov.hmrc.agentregistrationfrontend.views.html.providedetails.LlpMemberNamePage

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlpMemberNameController @Inject() (
  actions: Actions,
  mcc: MessagesControllerComponents,
  view: LlpMemberNamePage,
  memberProvideDetailsService: MemberProvideDetailsService
)
extends FrontendController(mcc, actions):

  def show: Action[AnyContent] = actions.getProvideDetailsInProgress:
    implicit request =>
      Ok(view(
        CompaniesHouseNameQueryForm.form
          .fill:
            request.memberProvidedDetails
              .companiesHouseMatch
              .map(_.memberNameQuery)
      ))

  def submit: Action[AnyContent] = actions.getProvideDetailsInProgress
    .ensureValidForm(CompaniesHouseNameQueryForm.form, implicit r => view(_))
    .async:
      implicit request: (MemberProvideDetailsRequest[AnyContent] & FormValue[CompaniesHouseNameQuery]) =>
        val validFormData = request.formValue
        memberProvideDetailsService
          .upsert(
            request.memberProvidedDetails
              .modify(_.companiesHouseMatch)
              .setTo(Some(CompaniesHouseMatch(
                memberNameQuery = validFormData,
                companiesHouseOfficer = None
              )))
          )
          .map: _ =>
            Redirect(AppRoutes.providedetails.CompaniesHouseMatchingController.show)
