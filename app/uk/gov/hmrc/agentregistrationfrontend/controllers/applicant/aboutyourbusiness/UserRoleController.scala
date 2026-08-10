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

package uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.aboutyourbusiness

import play.api.mvc.*
import uk.gov.hmrc.agentregistration.shared.BusinessType
import uk.gov.hmrc.agentregistration.shared.BusinessType.SoleTrader
import uk.gov.hmrc.agentregistration.shared.UserRole
import uk.gov.hmrc.agentregistrationfrontend.action.applicant.ApplicantActions
import uk.gov.hmrc.agentregistrationfrontend.controllers.applicant.FrontendController
import uk.gov.hmrc.agentregistrationfrontend.forms.UserRoleForm
import uk.gov.hmrc.agentregistrationfrontend.services.SessionService.*
import uk.gov.hmrc.agentregistrationfrontend.views.html.applicant.aboutyourbusiness.UserRolePage

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRoleController @Inject() (
  mcc: MessagesControllerComponents,
  actions: ApplicantActions,
  view: UserRolePage
)
extends FrontendController(mcc, actions):

  val baseAction: ActionBuilderWithData[EmptyTuple] = actions.action
    .ensure(
      condition = request => request.readBusinessType.isDefined,
      resultWhenConditionNotMet = _ => Redirect(AppRoutes.apply.aboutyourbusiness.BusinessTypeSessionController.show.url)
    )

  def show: Action[?] = baseAction:
    implicit request =>
      Ok(view(
        form = UserRoleForm.form.fill(request.readUserRole),
        userRoleOption = userRoleOptionForBusinessType(request.getBusinessType)
      ))

  def submit: Action[AnyContent] =
    baseAction
      .ensureValidForm(UserRoleForm.form, implicit request => view(_, userRoleOptionForBusinessType(request.getBusinessType))):
        implicit request =>
          Redirect(AppRoutes.apply.aboutyourbusiness.TypeOfSignInController.show.url).addToSession(request.get[UserRole])

  private def userRoleOptionForBusinessType(
    businessType: BusinessType
  ): UserRole =
    businessType match
      case SoleTrader => UserRole.Owner
      case BusinessType.Partnership.LimitedLiabilityPartnership => UserRole.Member
      case BusinessType.LimitedCompany => UserRole.Director
      case BusinessType.Partnership.GeneralPartnership => UserRole.Partner
      case BusinessType.Partnership.LimitedPartnership => UserRole.Partner
      case BusinessType.Partnership.ScottishPartnership => UserRole.Partner
      case BusinessType.Partnership.ScottishLimitedPartnership => UserRole.Partner
