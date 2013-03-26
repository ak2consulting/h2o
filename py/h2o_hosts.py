import getpass, json, h2o
import random, os
# UPDATE: all multi-machine testing will pass list of IP and base port addresses to H2O
# means we won't really on h2o self-discovery of cluster

def find_config(base):
    f = base
    if not os.path.exists(f): f = 'testdir_hosts/' + base
    if not os.path.exists(f): f = 'py/testdir_hosts/' + base
    if not os.path.exists(f):
        raise Exception("unable to find config %s" % base)
    return f

# None means the json will specify, or the default for json below
# only these two args override for now. can add more.
def build_cloud_with_hosts(node_count=None,
    use_hdfs=None, hdfs_name_node=None, hdfs_config=None,  hdfs_version=None,
    base_port=None,
    java_heap_GB=None, java_heap_MB=None, java_extra_args=None,
    use_multicast=True,
    **kwargs):

    # For seeing example of what we want in the json, if we add things
    #   import h2o_config

    # allow user to specify the config json at the command line. config_json is a global.
    # shouldn't need this??
    if h2o.config_json:
        configFilename = find_config(h2o.config_json)
    else:
        # configs may be in the testdir_hosts
        configFilename = find_config(h2o.default_hosts_file())

    h2o.verboseprint("Loading host config from", configFilename)
    with open(configFilename, 'rb') as fp:
         hostDict = json.load(fp)

    slow_connection = hostDict.setdefault('slow_connection', False)
    hostList = hostDict.setdefault('ip','127.0.0.1')

    h2oPerHost = hostDict.setdefault('h2o_per_host', 2)
    # default should avoid colliding with sri's demo cloud ports: 54321
    # we get some problems with sticky ports, during back to back tests in regressions
    # to avoid waiting, randomize the port to make it less likely?
    # at least for the hosts case
    offset = random.randint(0,31)
    basePort = hostDict.setdefault('base_port', 55300 + offset)

    username = hostDict.setdefault('username', None)
    key_filename = hostDict.setdefault('key_filename', None)
    
    use_multicast = hostDict.setdefault('use_multicast', use_multicast)

    useHdfs = hostDict.setdefault('use_hdfs', False)
    hdfsNameNode = hostDict.setdefault('hdfs_name_node', '192.168.1.151')
    hdfsVersion = hostDict.setdefault('hdfs_version', 'cdh3u5')
    hdfsConfig = hostDict.setdefault('hdfs_config', None)

    # default to none, which means the arg isn't used and java decides for us
    # useful for small dram systems, and for testing that
    javaHeapGB = hostDict.setdefault('java_heap_GB', None)
    javaHeapMB = hostDict.setdefault('java_heap_MB', None)
    javaExtraArgs = hostDict.setdefault('java_extra_args', None)

    use_home_for_ice = hostDict.setdefault('use_home_for_ice', False)

    # host aws configuration
    aws_credentials = hostDict.setdefault('aws_credentials', None)
    # a little hack to redirect import folder tests to an s3 folder
    redirect_import_folder_to_s3_path = hostDict.setdefault('redirect_import_folder_to_s3_path', None)

    inherit_io = hostDict.setdefault('inherit_io', False)

    # can override the json with a caller's argument
    # FIX! and we support passing the kwargs from above? but they don't override
    # json, ...so have to fix here if that's desired
    if node_count is not None:
        h2oPerHost = node_count

    if use_hdfs is not None:
        useHdfs = use_hdfs

    if hdfs_name_node is not None:
        hdfsNameNode = hdfs_name_node

    if hdfs_version is not None:
        hdfsVersion = hdfs_version

    if hdfs_config is not None:
        hdfsConfig = hdfs_config

    if java_heap_GB is not None:
        javaHeapGB = java_heap_GB

    if java_heap_MB is not None:
        javaHeapMB = java_heap_MB

    if java_extra_args is not None:
        javaExtraArgs = java_extra_args

    if base_port is not None:
        basePort = base_port

    h2o.verboseprint("host config: ", username,
        h2oPerHost, basePort,
        useHdfs, hdfsNameNode, hdfsVersion, hdfsConfig, javaHeapGB, javaHeapMB, use_home_for_ice,
        hostList, use_multicast, key_filename, aws_credentials, inherit_io, **kwargs)

    #********************
    global hosts
    # Update: special case hostList = ["127.0.0.1"] and use the normal build_cloud
    # this allows all the tests in testdir_host to be run with a special config that points to 127.0.0.1
    # hosts should be None for everyone if normal build_cloud is desired
    if hostList == ["127.0.0.1"]:
        hosts = None
    else:
        h2o.verboseprint("About to RemoteHost, likely bad ip if hangs")
        hosts = []
        for h in hostList:
            h2o.verboseprint("Connecting to:", h)
            key = None
            if(key_filename is not None):
                key = h2o.find_file(key_filename)
            hosts.append(h2o.RemoteHost(addr=h, user=username, key=key))

    timeoutSecs = 60

    # sandbox gets cleaned in build_cloud
    h2o.build_cloud(h2oPerHost,
            base_port=basePort, hosts=hosts, timeoutSecs=timeoutSecs,
            use_multicast=use_multicast,
            use_hdfs=useHdfs, hdfs_name_node=hdfsNameNode,
            hdfs_version=hdfsVersion, hdfs_config=hdfsConfig,
            java_heap_GB=javaHeapGB, java_heap_MB=javaHeapMB, java_extra_args=javaExtraArgs,
            use_home_for_ice=use_home_for_ice,
            aws_credentials=aws_credentials,
            inherit_io=inherit_io,
            redirect_import_folder_to_s3_path=redirect_import_folder_to_s3_path,
            **kwargs)
