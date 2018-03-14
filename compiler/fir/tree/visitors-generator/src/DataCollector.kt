/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.visitors.generator

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid

class DataCollector {

    private val references = mutableMapOf<String, List<String>>()
    private val packagePerClass = mutableMapOf<String, FqName>()

    fun readFile(file: KtFile) {
        file.acceptChildren(object : KtVisitorVoid() {

            override fun visitKtElement(element: KtElement) {
                super.visitKtElement(element)
                element.acceptChildren(this)
            }

            override fun visitClass(klass: KtClass) {
                if (klass.isInterface() && klass.name != null) {
                    packagePerClass[klass.name!!] = file.packageFqName
                    references[klass.name!!] =
                            klass.superTypeListEntries.mapNotNull {
                                it.typeAsUserType?.referencedName
                            }
                }

                super.visitClass(klass)
            }
        })
    }


    private fun Map<String, List<String>>.computeBackReferences(): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()

        this.forEach { (k, v) ->
            v.forEach {
                result.merge(it, listOf(k)) { a, b -> a + b }
            }
        }
        return result
    }


    fun computeResult(): ReferencesData {
        val back = references.computeBackReferences()

        val keysToKeep =
            generateSequence(listOf(FIR_ELEMENT_CLASS_NAME)) { it.flatMap { back[it].orEmpty() }.takeUnless { it.isEmpty() } }
                .flatten()
                .toSet()

        val cleanBack = back.filterKeys { it in keysToKeep }
        return ReferencesData(
            cleanBack.computeBackReferences(),
            cleanBack,
            packagePerClass.filterKeys { it in keysToKeep }.values.distinct()
        )
    }

    data class ReferencesData(val direct: Map<String, List<String>>, val back: Map<String, List<String>>, val usedPackages: List<FqName>)
}