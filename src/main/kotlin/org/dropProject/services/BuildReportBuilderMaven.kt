/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 Pedro Alves
 * %%
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
 * =========================LICENSE_END==================================
 */
package org.dropProject.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.dropProject.dao.*
import org.dropProject.dao.JUnitReport
import org.dropProject.dao.JacocoReport
import org.dropProject.dao.Submission
import org.dropProject.data.*
import org.dropProject.data.BuildReport
import org.dropProject.repository.AssignmentTestMethodRepository
import org.dropProject.repository.JUnitReportRepository
import org.dropProject.repository.JacocoReportRepository
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.util.logging.Logger

/**
 * This class contains functions that perform the creation of [BuildReport]s for both [Assignment]s and [Submission]s.
 */
@Service
class BuildReportBuilderMaven : BuildReportBuilder() {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Builds a BuildReport
     *
     * @param outputLines is a List of String with the output of a Maven build process
     * @param mavenizedProjectFolder is a String
     * @param assignment is an [Assignment]
     * @param submission is a [Submission]
     *
     * @return a [BuildReport]
     */
    override fun build(outputLines: List<String>,
              mavenizedProjectFolder: String,   
              assignment: Assignment,
              submission: Submission?) : BuildReport {

        //Get report from test execution (Submission)
        val junitReportFromDB : List<JUnitReport>? =
                if (submission != null) jUnitReportRepository.findBySubmissionId(submission.id) else null
        LOG.info("JUNIT Report From DB: ${junitReportFromDB}")

        val jUnitResults =
                if (junitReportFromDB != null && !junitReportFromDB.isEmpty()) {
                    LOG.info("Got jUnit Report from DB (submission)")
                    junitReportFromDB
                            .map { it -> junitResultsParserMaven.parseXml(it.xmlReport) }
                            .toList()
                } else {
                    try {
                        // LOG.info("Got jUnit Report from File System")
                        File("${mavenizedProjectFolder}/target/surefire-reports")
                                .walkTopDown()
                                .filter { it -> it.name.endsWith(".xml") }
                                .map { it -> junitResultsParserMaven.parseXml(it.readText()) }
                                .toList()
                    } catch (e: FileNotFoundException) {
                        LOG.info("Not found ${mavenizedProjectFolder}/target/surefire-reports. Probably this assignment doesn't produce test results")
                        emptyList<JUnitResults>()
                    }
                }
        LOG.info("JUNIT Results: ${jUnitResults}")

        //Submission (report from Jacoco)
        val jacocoReportFromDB : List<JacocoReport>? =
                if (submission != null) jacocoReportRepository.findBySubmissionId(submission.id)
                else null

        //Submission results?
        val jacocoResults =
            if (jacocoReportFromDB != null && !jacocoReportFromDB.isEmpty()) {
                LOG.info("Got Jacoco Report from DB (submission)")
                jacocoReportFromDB
                        .map { it -> jacocoResultsParser.parseCsv(it.csvReport) }
                        .toList()

            } else {
                emptyList<JacocoResults>()
            }

        //Test methods of assignment (in repository)
        val assignmentTestMethods =
                if (submission != null) {
                    assignmentTestMethodRepository.findByAssignmentId(assignment.id)
                } else {
                    emptyList()
                }
        LOG.info("Assignment Test Methods: ${jUnitResults}")

        LOG.info("Created build report for Maven.")
        return BuildReportMaven(outputLines, mavenizedProjectFolder, assignment, jUnitResults, jacocoResults,
                assignmentTestMethods)
    }
}
