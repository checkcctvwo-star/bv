package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.account.AccountModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [AccountModule::class])
@ComponentScan
class BiliApiModule