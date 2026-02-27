package com.example.settings.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate

class SettingsProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private val SETTINGS_GROUP_ANNOTATION = "com.example.settings.annotations.SettingsGroup"

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver
            .getSymbolsWithAnnotation(SETTINGS_GROUP_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()

        val unprocessed = mutableListOf<KSAnnotated>()

        symbols.forEach { classDecl ->
            if (!classDecl.validate()) {
                unprocessed.add(classDecl)
                return@forEach
            }
            if (classDecl.classKind != ClassKind.INTERFACE) {
                logger.error("@SettingsGroup must annotate an interface", classDecl)
                return@forEach
            }
            processSettingsInterface(classDecl)
        }

        return unprocessed
    }

    private fun processSettingsInterface(classDecl: KSClassDeclaration) {
        val groupAnnotation = classDecl.annotations
            .first { it.shortName.asString() == "SettingsGroup" }
        val groupName = groupAnnotation.arguments
            .first { it.name?.asString() == "name" }.value as String

        val packageName = classDecl.packageName.asString()
        val interfaceName = classDecl.simpleName.asString()

        val settingProperties = classDecl.getAllProperties()
            .mapNotNull { prop -> extractSettingInfo(prop) }
            .toList()

        if (settingProperties.isEmpty()) {
            logger.warn("@SettingsGroup interface '$interfaceName' has no @XxxSetting properties", classDecl)
        }

        val gen = SettingsCodeGenerator(codeGenerator, logger)
        gen.generateStore(packageName, interfaceName, groupName, settingProperties)
        gen.generateModule(packageName, interfaceName, groupName, settingProperties)
    }

    private fun extractSettingInfo(prop: KSPropertyDeclaration): SettingInfo? {
        val propName = prop.simpleName.asString()
        return prop.annotations.toList().firstNotNullOfOrNull { ann ->
            when (ann.shortName.asString()) {
                "StringSetting" -> SettingInfo(
                    propName = propName,
                    type = SettingType.STRING,
                    key = ann.arguments.first { it.name?.asString() == "key" }.value as String,
                    defaultValue = ann.arguments.first { it.name?.asString() == "default" }.value as String
                )
                "IntSetting" -> {
                    val raw = ann.arguments.first { it.name?.asString() == "default" }.value
                    SettingInfo(
                        propName = propName,
                        type = SettingType.INT,
                        key = ann.arguments.first { it.name?.asString() == "key" }.value as String,
                        defaultValue = (raw as? Int) ?: 0
                    )
                }
                "LongSetting" -> {
                    val raw = ann.arguments.first { it.name?.asString() == "default" }.value
                    SettingInfo(
                        propName = propName,
                        type = SettingType.LONG,
                        key = ann.arguments.first { it.name?.asString() == "key" }.value as String,
                        defaultValue = when (raw) {
                            is Long -> raw
                            is Int -> raw.toLong()
                            else -> 0L
                        }
                    )
                }
                "FloatSetting" -> {
                    val raw = ann.arguments.first { it.name?.asString() == "default" }.value
                    SettingInfo(
                        propName = propName,
                        type = SettingType.FLOAT,
                        key = ann.arguments.first { it.name?.asString() == "key" }.value as String,
                        defaultValue = when (raw) {
                            is Float -> raw
                            is Double -> raw.toFloat()
                            else -> 0f
                        }
                    )
                }
                "BoolSetting" -> SettingInfo(
                    propName = propName,
                    type = SettingType.BOOL,
                    key = ann.arguments.first { it.name?.asString() == "key" }.value as String,
                    defaultValue = ann.arguments.first { it.name?.asString() == "default" }.value as Boolean
                )
                else -> null
            }
        }
    }
}
