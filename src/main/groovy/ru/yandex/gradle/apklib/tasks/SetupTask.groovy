/*
 * Copyright 2012 Yandex.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.yandex.gradle.apklib.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.tasks.TaskAction
import ru.yandex.gradle.apklib.SdkHelper

/**
 * Created by Vladimir Grachev (vgrachev@)
 * Date: 31.05.12
 * Time: 13:51
 */
class SetupTask extends DefaultTask {

    def mapName = 'teamcity'

    @TaskAction
    def setup() {
        setupSdkDir()
        setupNdkDir()
        setupLibrary()
        setupProguardProperties()
        
        loadAntProperties()
        loadFromMap(mapName)

        setupVersion()

        setAntProps()
    }

    def setupVersion() {
        project.ext['version.code'] = getIntVersion(project.version)
        project.ext['version.name'] = getStringVersion(project.version)

        if (project.properties.containsKey('release.branch') &&
            "true" == project.properties['release.branch']) {
            releaseVersion()
        }
        else if (project.properties.containsKey(mapName)) {
            continiousIntegrationVersion()
        }
        else {
            debugVersion()
        }
    }

    def getIntVersion(String version) {
        def result = version.replaceAll('\\.', '').find(/\d+/)
        if (result == null) return "123"
        while (result.length() < 3) result += "0"
        return result
    }

    def getStringVersion(String version) {
        def result = version.replace("-SNAPSHOT", "")
        if (result == null) return "1.23"
        return result
    }

    def getReleaseVersion(String version, String buildNumber) {
        return version.replace("-SNAPSHOT", "");
    }

    def getСiVersion(String version) {
        return version.replace("-SNAPSHOT", "") + "-SNAPSHOT";
    }

    def getDebugVersion(String version) {
        return version.replace("-SNAPSHOT", "") + "-SNAPSHOT";
    }

    def setAntProps() {

        project.properties.each { property ->
            if (property.key.startsWith("ant.") && !property.key.contains("proguard.config")) {
                project.ant.properties[property.key.replace("ant.", "")] = property.value
            }
        }

        if (project.properties.containsKey('build.number')) {
            project.ant.properties['build.number'] = project['build.number']
        }

        project.ant.properties['ant.project.name'] = project.name
        project.ant.properties['version.code'] = project['version.code']
        project.ant.properties['version.name'] = project['version.name']
        if (project.properties.containsKey('dpi') && project.properties['dpi'] != 'all') {
            project.ant.properties['aapt.resource.filter'] = project.properties['dpi']
        }

        if (project.properties['android.library'] != "true") {
            def manifestName = project.properties['manifest.name']

            if (project.properties.containsKey("manifest.name")) {
                def manifestFile = "AndroidManifest.${manifestName}.xml"
                def manifestPath = "$project.projectDir/AndroidManifest.${manifestName}.xml"

                project.ant.properties["manifest.file"] = manifestFile
                project.ant.properties["manifest.abs.file"] = manifestPath
            }
        }
    }

    def setupLibrary() {
        logger.info("SETUP LIBRARY")
        def files = [new File("$project.projectDir/ant.properties"), new File("$project.projectDir/project.properties")]

        String result = 'false'

        logger.info("SETUP LIBRARY Scanning files")
        files.each { file ->
            try {
                def props = new Properties();

                file.withInputStream { props.load(it) }

                logger.info("SETUP LIBRARY Reading file: $file.name")

                if (props.stringPropertyNames().contains("android.library")) {
                    logger.info("SETUP LIBRARY Props contain android.library")
                    if (props["android.library"] == 'true') {
                        result = 'true'
                    }
                } else {
                    logger.info("SETUP LIBRARY Props don't contain android.library")
                }
            }
            catch (FileNotFoundException) {
                logger.warn("SETUP LIBRARY No $project.projectDir/$file.name file found.")
            }
        }

        logger.info("SETUP LIBRARY Result = $result")

        project.ext['android.library'] = result
    }

    def setupSdkDir() {
        if (project.properties.containsKey('sdk.dir')) {
            logger.info("Setting up 'sdk.dir' from project properties: " + project.properties['sdk.dir'])
            project.ant.properties['sdk.dir'] = project.properties['sdk.dir']
        }
        else if (System.getenv().containsKey("ANDROID_SDK_HOME")) {
            logger.info("Setting up 'sdk.dir' from environment: " + System.getenv().get("ANDROID_SDK_HOME"))
            project.ant.properties['sdk.dir'] = System.getenv().get("ANDROID_SDK_HOME")
            project.ext['sdk.dir'] = System.getenv().get("ANDROID_SDK_HOME")
        }
        else if (System.getenv().containsKey("ANDROID_HOME")) {
            logger.info("Setting up 'sdk.dir' from environment: " + System.getenv().get("ANDROID_HOME"))
            project.ant.properties['sdk.dir'] = System.getenv().get("ANDROID_HOME")
            project.ext['sdk.dir'] = System.getenv().get("ANDROID_HOME")
        }

        if (project.properties.containsKey('use21') && project.properties['use21'] == "true") {
            project.ant.properties['sdk.dir'] += ".21"
        }

        logger.lifecycle("SDK DIR: " + project.properties['sdk.dir'])

        if (!project.properties.containsKey('sdk.dir')) {
            logger.error("""
    =======================================================================
        Please, setup ANDROID_SDK_HOME environment variable to procced.
    =======================================================================
            """);

            throw new GradleScriptException("ANDROID_SDK_HOME is undefined.")
        }
    }

    def setupNdkDir() {
        if (project.properties.containsKey('ndk.dir')) {
            logger.info("Setting up 'ndk.dir' from project properties: " + project.properties['ndk.dir'])
            project.ant.properties['ndk.dir'] = project.properties['ndk.dir']
        }
        else if (System.getenv().containsKey("ANDROID_NDK_HOME")) {
            logger.info("Setting up 'ndk.dir' from environment: " + System.getenv().get("ANDROID_NDK_HOME"))
            project.ant.properties['ndk.dir'] = System.getenv().get("ANDROID_NDK_HOME")
            project.ext['ndk.dir'] = System.getenv().get("ANDROID_NDK_HOME")
        }
        else if (System.getenv().containsKey("NDK_HOME")) {
            logger.info("Setting up 'ndk.dir' from environment: " + System.getenv().get("NDK_HOME"))
            project.ant.properties['ndk.dir'] = System.getenv().get("NDK_HOME")
            project.ext['ndk.dir'] = System.getenv().get("NDK_HOME")
        }
        logger.lifecycle("NDK DIR: " + project.properties['ndk.dir'])

        if (!project.properties.containsKey('ndk.dir') && SdkHelper.isNdkBuild(project)) {
            logger.error("""
    =======================================================================
        Please, setup ANDROID_NDK_HOME environment variable to procced.
    =======================================================================
            """);

            throw new GradleScriptException("ANDROID_NDK_HOME is undefined.")
        }
    }

    def setupProguardProperties(){
        if (project.properties.containsKey('proguard.config')) {
            logger.info("Setting up 'proguard.config' from project properties: " + project.properties['proguard.config'])
            project.ant.properties['proguard.config'] = project.properties['proguard.config']
        }
        else if (System.getenv().containsKey("proguard.config")) {
            logger.info("Setting up 'proguard.config' from environment: " + System.getenv().get("proguard.config"))
            project.ant.properties['proguard.config'] = System.getenv().get("proguard.config")
            project.ext['proguard.config'] = System.getenv().get("proguard.config")
        }
//        else {
//            logger.info("Setting up 'proguard.config' default: " + project.properties['sdk.dir']+"/tools/proguard/proguard-android.txt")
//            project.ant.properties['proguard.config'] = project.properties['sdk.dir']+"/tools/proguard/proguard-android.txt"
//            project.ext['proguard.config'] = project.properties['sdk.dir']+"/tools/proguard/proguard-android.txt"
//        }
//        logger.lifecycle("PROGUARD CONFIG DIR: " + project.properties['proguard.config'])
    }
    
    def loadAntProperties() {
        def count1 = loadAntPropertiesFromFile("project.properties")
        def count2 = loadAntPropertiesFromFile("ant.properties")
        project.ext.set("library.references.count", Math.max(count1, count2))
    }

    int loadAntPropertiesFromFile(String file) {
        int count = 1
        int num = 1

        def skipReference = null

        try {
            def props = new Properties();
            new File("$project.projectDir/$file").withInputStream { props.load(it) }

            while (props.stringPropertyNames().contains("android.library.reference." + num)) {
                if ("$ExportTask.EXPORT_PATH/" == props["android.library.reference." + num]) {
                    skipReference = "android.library.reference." + num
                }
                else {
                    String value = props["android.library.reference." + num]
                    props.remove("android.library.reference." + num)
                    props["android.library.reference." + count] = value

                    logger.info("Found apklib in $file: android.library.reference." + count + " = " + props["android.library.reference." + count])
                    count++
                }
                num++
            }

            if (skipReference != null) {
                props.remove(skipReference)
            }

//            props.each {
//                logger.info("ADD ANT PROPERTY 'ant.$it.key' from $file file")
//                if ("$it.key".contains("android.library.reference")) {
//                    project.ext.set("$it.key", it.value)
//                }
//                else {
//                    project.ext.set("ant.$it.key", it.value)
//                }
//            }

            return count
        }
        catch (FileNotFoundException e) {
            logger.warn("File $project.projectDir/$file not found.")
        }
    }

    def loadFromMap(String map) {
        if (!project.properties.containsKey(map)){
            project.ext['preprocess.beta.features'] = 'true'
            return
        }

        project.ext['preprocess.beta.features'] = 'false'

        def props = project.properties[map]

        props.each{
            project.ext[it.key] = it.value
        }
    }

    def releaseVersion() {
        project.ext['version.original'] = project.version
        project.version = getReleaseVersion(project.version, project['build.number'])
        logger.info("Project version: $project.version")
    }

    def continiousIntegrationVersion() {
        project.ext['version.original'] = project.version
        project.version = getСiVersion(project.version)
        logger.info("Project version: $project.version")
    }

    def debugVersion() {
        project.ext['version.original'] = project.version
        project.version = getDebugVersion(project.version)
        logger.info("Project version: $project.version")
    }
}
