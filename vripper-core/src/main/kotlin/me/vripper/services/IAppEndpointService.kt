package me.vripper.services

interface IAppEndpointService :
    IPostCommandEndpoint,
    IPostQueryEndpoint,
    IPostEventEndpoint,
    IThreadEndpoint,
    ISettingsEndpoint,
    IDownloadEndpoint,
    ILogEndpoint,
    ITaskEndpoint,
    IEndpointLifecycle
