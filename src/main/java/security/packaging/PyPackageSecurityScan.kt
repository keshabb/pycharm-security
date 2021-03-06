package security.packaging

import com.google.common.collect.Sets
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import security.settings.SecuritySettings

object PyPackageSecurityScan {
    var NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Python Package Security Checker")

    fun checkPackages(project: Project): Boolean?{
        val pythonSdks = getPythonSdks(project)
        if (pythonSdks.isEmpty()){
            returnError(project)
            return null
        }
        try {
            if (SecuritySettings.instance.safetyDbMode == SecuritySettings.SafetyDbType.Disabled)
                return false
            else if (SecuritySettings.instance.safetyDbMode == SecuritySettings.SafetyDbType.Bundled)
                checkPackagesInSdks(pythonSdks, project, SafetyDbChecker())
            else if (SecuritySettings.instance.safetyDbMode == SecuritySettings.SafetyDbType.Api)
                checkPackagesInSdks(pythonSdks, project, SafetyDbChecker(SecuritySettings.instance.pyupApiKey, SecuritySettings.instance.pyupApiUrl))
            else if (SecuritySettings.instance.safetyDbMode == SecuritySettings.SafetyDbType.Custom)
                checkPackagesInSdks(pythonSdks, project, SafetyDbChecker("", SecuritySettings.instance.pyupCustomUrl))
            else if (SecuritySettings.instance.safetyDbMode == SecuritySettings.SafetyDbType.Snyk)
                checkPackagesInSdks(pythonSdks, project, SnykChecker(SecuritySettings.instance.snykApiKey, SecuritySettings.instance.snykOrgId))
            return true
        } catch (ex: PackageCheckerLoadException){
            backendError(project, ex.message)
            return null
        }
    }

    fun checkPackagesInSdks(pythonSdks: Set<Sdk>, project: Project, packageChecker: PackageChecker): Int {
        var total = 0
        for (sdk in pythonSdks) {
            val packageManager = PyPackageManager.getInstance(sdk)
            packageManager.refreshAndGetPackages(true)
            total += inspectLocalPackages(packageManager, project, packageChecker) ?: 0
        }
        return total
    }

    fun inspectLocalPackages(packageManager: PyPackageManager, project: Project, packageChecker: PackageChecker): Int? {
        var matches = 0
        if (packageManager.packages == null) {
            returnError(project)
            return null
        }
        packageManager.packages!!.filter { packageChecker.hasMatch(it) }.forEach { pack ->
            packageChecker.getMatches(pack).forEach { issue ->
                matches++
                showFoundIssueWarning(pack, issue, project)
            }
        }
        if (matches == 0) {
            showNoMatchesInformation(project)
            return 0
        }
        showTotalIssuesWarning(matches, project)
        return matches
    }

    private fun backendError(project: Project, message: String?){
        NOTIFICATION_GROUP
                .createNotification("Could not check Python packages", null,
                        "Could not fetch API to validate records. Check your API details.\n$message",
                        NotificationType.ERROR)
                .notify(project)
    }

    private fun returnError(project: Project){
        NOTIFICATION_GROUP
                .createNotification("Could not check Python packages", null,
                        "Could not verify security of Python packages, unable to locate configured Python Interpreter. Please configure your interpreter.",
                        NotificationType.INFORMATION)
                .notify(project)
    }

    private fun showTotalIssuesWarning(matches: Int, project: Project) {
        NOTIFICATION_GROUP
                .createNotification("Completed checking packages", null,
                        "Found $matches potential security issues with your installed packages.",
                        NotificationType.WARNING)
                .notify(project)
    }

    private fun showNoMatchesInformation(project: Project) {
        NOTIFICATION_GROUP
                .createNotification("Completed checking packages", null,
                        "Found no known security issues with your installed packages.",
                        NotificationType.INFORMATION)
                .notify(project)
    }

    private fun showFoundIssueWarning(pack: PyPackage?, issue: PackageIssue, project: Project) {
        NOTIFICATION_GROUP
                .createNotification("Found Security Vulnerability in $pack package", null,
                        issue.getMessage(),
                        NotificationType.WARNING,
                        NotificationListener.URL_OPENING_LISTENER
                ).notify(project)
    }

    fun getPythonSdks(project: Project): Set<Sdk> {
        val pythonSdks: MutableSet<Sdk> = Sets.newLinkedHashSet()
        for (module in ModuleManager.getInstance(project).modules) {
            val sdk = PythonSdkUtil.findPythonSdk(module)
            if (sdk != null && sdk.sdkType is PythonSdkType) {
                pythonSdks.add(sdk)
            }
        }
        return pythonSdks
    }
}