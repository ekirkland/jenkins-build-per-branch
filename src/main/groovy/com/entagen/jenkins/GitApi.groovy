package com.entagen.jenkins

import java.util.regex.Pattern
import groovy.json.JsonSlurper

class GitApi {
    String gitUrl
    Pattern branchNameFilter = null
    String gitHubApiUrl = null
    String gitHubAccessToken = null

    public List<String> getBranchNames() {
        String command = "git ls-remote --heads ${gitUrl}"
        List<String> branchNames = []

        eachResultLine(command) { String line ->
            String branchNameRegex = "^.*\trefs/heads/(.*)\$"
            String branchName = line.find(branchNameRegex) { full, branchName -> branchName }
            Boolean selected = passesFilter(branchName)

            String shaRegex = "^(.*)\trefs/heads/.*\$"
            String commitSha = line.find(shaRegex) { full, commitSha -> commitSha }

            if(selected) {
                println "$line"
                if(gitHubApiUrl && gitHubAccessToken) {

                    def response = ["curl", "-H", "Authorization: token ${gitHubAccessToken}", "${gitHubApiUrl}/commits/${commitSha}"].execute().text
                    def dateString = new JsonSlurper().parseText(response).commit.author.date;
                    def duration = new Date() - Date.parse("yyyy-MM-dd", dateString.substring(0, 10))
                    println "Found date: ${dateString} ${duration}"
                }
                println "\t" + (selected ? "* " : "  ") + "$line"
                if (selected) branchNames << branchName
            }

        }

        return branchNames
    }

    public Boolean passesFilter(String branchName) {
        if (!branchName) return false
        if (!branchNameFilter) return true
        return branchName ==~ branchNameFilter
    }

    // assumes all commands are "safe", if we implement any destructive git commands, we'd want to separate those out for a dry-run
    public void eachResultLine(String command, Closure closure) {
        runCommand(command).eachLine { String line ->
            closure(line)
        }
    }

    public String runCommand(String command) {
        println "executing command: $command"
        def process = command.execute()
        def inputStream = process.getInputStream()
        def gitOutput = ""

        while (true) {
            int readByte = inputStream.read()
            if (readByte == -1) break // EOF
            byte[] bytes = new byte[1]
            bytes[0] = readByte
            gitOutput = gitOutput.concat(new String(bytes))
        }
        process.waitFor()

        if (process.exitValue() == 0) {
            return gitOutput;
        } else {
            String errorText = process.errorStream.text?.trim()
            println "error executing command: $command"
            println errorText
            throw new Exception("Error executing command: $command -> $errorText")
        }
    }

}
