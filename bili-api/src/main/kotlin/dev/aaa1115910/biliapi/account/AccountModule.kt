package dev.aaa1115910.biliapi.account

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * 注册 account 包下的 @Single（AccountResolver）。
 * BiliApiModule 的 @ComponentScan 只扫 repositories 子包，扫不到 account 包，
 * 故单独建此 Module 并由 BiliApiModule include，确保 AccountResolver 被注册进 Koin 容器。
 */
@Module
@ComponentScan
class AccountModule
