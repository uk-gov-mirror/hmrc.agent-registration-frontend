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

package uk.gov.hmrc.agentregistrationfrontend.config

import play.api.i18n.MessagesApi
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import uk.gov.hmrc.agentregistrationfrontend.controllers.AppRoutes

import javax.inject.Inject

class AddressLookupConfig @Inject() (
  appConfig: AppConfig,
  messagesApi: MessagesApi
) {

  def createJourneyConfig(continueUrl: String): JsObject =

    Json.obj(
      "version" -> 2,
      "options" -> Json.obj(
        "continueUrl" -> s"${appConfig.thisFrontendBaseUrl}$continueUrl",
        "includeHMRCBranding" -> false,
        "useNewGovUkServiceNavigation" -> true,
        "signOutHref" -> s"${appConfig.thisFrontendBaseUrl}${AppRoutes.SignOutController.signOut.url}",
        "selectPageConfig" -> Json.obj(
          "proposedListLimit" -> 30,
          "showSearchLinkAgain" -> true
        ),
        "allowedCountryCodes" -> Json.arr("GB"),
        "ukMode" -> true,
        "confirmPageConfig" -> Json.obj(
          "showChangeLink" -> true,
          "showSubHeadingAndInfo" -> true,
          "showSearchAgainLink" -> false,
          "showConfirmChangeText" -> true
        ),
        "manualAddressEntryConfig" -> Json.obj(
          "mandatoryFields" -> Json.obj(
            "addressLine1" -> true,
            "town" -> true,
            "postcode" -> true
          ),
          "showOrganisationName" -> false
        ),
        "timeoutConfig" -> Json.obj(
          "timeoutAmount" -> 900,
          "timeoutUrl" -> s"${appConfig.thisFrontendBaseUrl}${AppRoutes.SignOutController.timeOut.url}"
        )
      ),
      "labels" -> Json.obj(
        "en" -> Json.obj(
          "appLevelLabels" -> Json.obj(
            "navTitle" -> messagesApi("service.name")(using AppLangs.en)
          ),
          "lookupPageLabels" -> Json.obj(
            "title" -> messagesApi("address.lookup.title")(using AppLangs.en),
            "heading" -> messagesApi("address.lookup.header")(using AppLangs.en)
          ),
          "editPageLabels" -> Json.obj(
            "title" -> messagesApi("address.lookup.editPageLabels.title")(using AppLangs.en),
            "heading" -> messagesApi("address.lookup.editPageLabels.header")(using AppLangs.en)
          )
        ),
        "cy" -> Json.obj(
          "appLevelLabels" -> Json.obj(
            "navTitle" -> messagesApi("service.name")(using AppLangs.cy)
          ),
          "lookupPageLabels" -> Json.obj(
            "title" -> messagesApi("address.lookup.title")(using AppLangs.cy),
            "heading" -> messagesApi("address.lookup.header")(using AppLangs.cy)
          ),
          "editPageLabels" -> Json.obj(
            "title" -> messagesApi("address.lookup.editPageLabels.title")(using AppLangs.cy),
            "heading" -> messagesApi("address.lookup.editPageLabels.header")(using AppLangs.cy)
          )
        )
      )
    )

}
