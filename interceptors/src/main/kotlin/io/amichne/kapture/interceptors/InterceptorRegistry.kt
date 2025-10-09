package io.amichne.kapture.interceptors

import io.amichne.kapture.interceptors.session.SessionTrackingInterceptor

object InterceptorRegistry {
    val interceptors: List<GitInterceptor> = listOf(
        BranchPolicyInterceptor(),
        StatusGateInterceptor(),
        CommitMessageInterceptor(),
        SessionTrackingInterceptor()
    )
}
