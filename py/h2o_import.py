import h2o, h2o_cmd
import time, re

def setupImportS3(node=None, bucket='home-0xdiag-datasets'):
    if not bucket: raise Exception('No S3 bucket specified')
    if not node: node = h2o.nodes[0]
    importS3Result = node.import_s3(bucket)
    # too many files now to print
    ### print h2o.dump_json(importS3Result)
    return importS3Result

# assumes you call setupImportS3 first
def parseImportS3File(node=None, 
    csvFilename='covtype.data', path='home-0xdiag-datasets', key2=None, 
    timeoutSecs=360, retryDelaySecs=2, initialDelaySecs=1, pollTimeoutSecs=60, noise=None,
    benchmarkLogging=None, noPoll=False, **kwargs):

    if not node: node = h2o.nodes[0]
    if not csvFilename: raise Exception('parseImportS3File: No csvFilename')
    s3Key= "s3://" + path + "/" + csvFilename

    # We like the short parse key2 name. 
    # We don't drop anything from csvFilename, unlike H2O default
    if key2 is None:
        # don't rely on h2o default key name
        myKey2 = csvFilename + '.hex'
    else:
        myKey2 = key2

    print "Waiting for the slow parse of the file:", csvFilename
    parseKey = node.parse(s3Key, myKey2, 
        timeoutSecs, retryDelaySecs, initialDelaySecs, pollTimeoutSecs, noise, 
        benchmarkLogging, noPoll, **kwargs)
    # a hack so we know what the source_key was, bask at the caller
    parseKey['source_key'] = s3Key
    print "\nParse result:", parseKey
    return parseKey

def setupImportFolder(node=None, path='/home/0xdiag/datasets'):
    # a little hack to redirect import folder tests to an s3 folder
    # we don't have any "state" other than per node, so stuck this sort-of-global
    # test config state (which gets set only from the config json use-case)
    # on the nodes. The only globals we have are command line args..so lets keep that
    # Really should have a class for global H2O cloud state? or test state?
    if not node: node = h2o.nodes[0]
    if node.redirect_import_folder_to_s3_path: 
        # FIX! make bucket vary depending on path
        bucket = 'home-0xdiag-datasets'
        importFolderResult = setupImportS3(node=node, bucket=bucket)
    else:
        importFolderResult = node.import_files(path)
    ### h2o.dump_json(importFolderResult)
    return importFolderResult

# assumes you call setupImportFolder first
def parseImportFolderFile(node=None, csvFilename=None, path=None, key2=None,
    timeoutSecs=30, retryDelaySecs=0.5, initialDelaySecs=1, pollTimeoutSecs=60, noise=None,
    benchmarkLogging=None, noPoll=False, **kwargs):
    if not node: node = h2o.nodes[0]
    # a little hack to redirect import folder tests to an s3 folder
    # TEMP hack: translate /home/0xdiag/datasets to /home-0xdiag-datasets

    if not csvFilename: raise Exception('parseImportFolderFile: No csvFilename')

    # We like the short parse key2 name. 
    # We don't drop anything from csvFilename, unlike H2O default
    if key2 is None:
        # don't rely on h2o default key name
        myKey2 = csvFilename + '.hex'
    else:
        myKey2 = key2

    print "Waiting for the slow parse of the file:", csvFilename

    if node.redirect_import_folder_to_s3_path:
        path = re.sub('/home/0xdiag/datasets', 'home-0xdiag-datasets', path)
        parseKey = parseImportS3File(node, csvFilename, path, myKey2,
            timeoutSecs, retryDelaySecs, initialDelaySecs, pollTimeoutSecs, noise, 
            benchmarkLogging, noPoll)
    else:
        importKey = "nfs:/" + path + "/" + csvFilename
        parseKey = node.parse(importKey, myKey2, 
            timeoutSecs, retryDelaySecs, initialDelaySecs, pollTimeoutSecs, noise, 
            benchmarkLogging, noPoll, **kwargs)
        # a hack so we know what the source_key was, bask at the caller
        parseKey['source_key'] = importKey
        print "\nParse result:", parseKey
    return parseKey

def setupImportHdfs(node=None, path=None):
    if not node: node = h2o.nodes[0]
    hdfsPrefix = 'hdfs://' + node.hdfs_name_node
    if path is None:
        URI = hdfsPrefix + '/datasets'
    else:
        URI = hdfsPrefix + path

    importHdfsResult = node.import_hdfs(URI)
    # too many hdfs keys to print now
    h2o.verboseprint(h2o.dump_json(importHdfsResult))
    return importHdfsResult

def parseImportHdfsFile(node=None, csvFilename=None, path=None, 
    timeoutSecs=3600, retryDelaySecs=2, initialDelaySecs=1, pollTimeoutSecs=60, noise=None,
    benchmarkLogging=None, noPoll=False, **kwargs):
    if not csvFilename: raise Exception('No csvFilename parameter in parseImportHdfsFile')
    if not node: node = h2o.nodes[0]

    hdfsPrefix = 'hdfs://' + node.hdfs_name_node
    if path is None:
        URI = hdfsPrefix + '/datasets'
    else:
        URI = hdfsPrefix + path

    hdfsKey = URI + "/" + csvFilename
    print "parseHdfsFile hdfsKey:", hdfsKey
    inspect = h2o_cmd.runInspect(key=hdfsKey)
    print "parseHdfsFile:", inspect

    parseKey = node.parse(hdfsKey, csvFilename + ".hex",
        timeoutSecs, retryDelaySecs, initialDelaySecs, pollTimeoutSecs, noise, 
        benchmarkLogging, noPoll, **kwargs)
    # a hack so we know what the source_key was, bask at the caller
    parseKey['source_key'] = hdfsKey
    print "parseHdfsFile:", parseKey
    return parseKey
