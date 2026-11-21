package com.mycar.app.features.localtest

import com.mycar.app.BuildConfig
import com.mycar.app.features.sesion.data.ClientesSesion

object LocalTestClient {
    const val QA_TOKEN = "mycar-local-qa"

    fun api(): LocalTestApi =
        ClientesSesion.publico(BuildConfig.API_BASE_URL).create(LocalTestApi::class.java)

    fun disponibleEnApk(): Boolean = BuildConfig.LOCAL_TEST_TOOLS
}
