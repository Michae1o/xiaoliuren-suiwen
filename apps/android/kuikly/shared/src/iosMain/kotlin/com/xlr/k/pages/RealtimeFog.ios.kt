package com.xlr.k.pages

/* iOS 端也有实时雾了：iosApp/XLRRealtimeFog.h/.m + FogShader.metal
   （MTKView 30fps + Metal，由安卓 RealtimeFogView.kt 的 AGSL 逐行翻译）。
   返回 true 之后，雾档 = 二档时会走原生那层，和安卓同一条路径。

   ⚠ 打开这个开关的前提是宿主真的注册了 XLRRealtimeFog 组件：Kuikly 按 viewName()
     的返回值找同名 ObjC 类。如果宿主里没有这个类，组件会创建失败、这块就是空的。
     所以壳那边 XLRRealtimeFog.m 必须一起打包（它已在 project.yml 的 sources 里）。 */
internal actual fun realtimeFogAvailable(): Boolean = true
