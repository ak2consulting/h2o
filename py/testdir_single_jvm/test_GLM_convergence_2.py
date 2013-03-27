import unittest
import random, sys, time, os, re
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd, h2o_hosts, h2o_browse as h2b, h2o_import as h2i, h2o_glm

def write_syn_dataset(csvPathname, rowCount, colCount, SEED):
    r1 = random.Random(SEED)
    # keep a single thread from the original SEED, for repeatability.
    SEED2 = r1.randint(0, sys.maxint)
    r2 = random.Random(SEED2)
    dsf = open(csvPathname, "w+")

    # complete separation
    for i in range(rowCount):
        # not using colCount. Just one col
        rowData = []
        ri1 = i
        rowTotal = ri1
        rowData.append(ri1)

        if i > (rowCount/2):
            result = 1
        else:
            result = 0

        rowData.append(str(result))
        ### print colCount, rowTotal, result
        rowDataCsv = ",".join(map(str,rowData))
        dsf.write(rowDataCsv + "\n")

    dsf.close()

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        global SEED
        ### SEED = random.randint(0, sys.maxint)
        ### SEED = 8389506152467586392
        SEED = 2437856391921621805
        random.seed(SEED)
        print "\nUsing random seed:", SEED
        localhost = h2o.decide_if_localhost()
        if (localhost):
            h2o.build_cloud(1)
        else:
            h2o_hosts.build_cloud_with_hosts()

    @classmethod
    def tearDownClass(cls):
        ### time.sleep(3600)
        h2o.tear_down_cloud()

    def test_GLM_convergence_2(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        tryList = [
            (100, 1,  'cD', 300),
            # (100, 100, 'cE', 300),
            # (100, 200, 'cF', 300),
            # (100, 300, 'cG', 300),
            # (100, 400, 'cH', 300),
            # (100, 500, 'cI', 300),
        ]

        ### h2b.browseTheCloud()
        lenNodes = len(h2o.nodes)

        USEKNOWNFAILURE = False
        for (rowCount, colCount, key2, timeoutSecs) in tryList:
            SEEDPERFILE = random.randint(0, sys.maxint)
            csvFilename = 'syn_%s_%sx%s.csv' % (SEEDPERFILE,rowCount,colCount)
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename
            print "\nCreating random", csvPathname
            write_syn_dataset(csvPathname, rowCount, colCount, SEEDPERFILE)

            if USEKNOWNFAILURE:
                csvFilename = 'failtoconverge_100x50.csv'
                csvPathname = h2o.find_file('smalldata/logreg/' + csvFilename)

            parseKey = h2o_cmd.parseFile(None, csvPathname, key2=key2, timeoutSecs=10)
            print csvFilename, 'parse time:', parseKey['response']['time']
            print "Parse result['destination_key']:", parseKey['destination_key']
            inspect = h2o_cmd.runInspect(None, parseKey['destination_key'])
            print "\n" + csvFilename

            y = colCount
            kwargs = {
                    'max_iter': 40, 
                    'lambda': 1e0,
                    'alpha': 0.5,
                    'weight': 1.0,
                    'link': 'familyDefault',
                    'num_cross_validation_folds': 0,
                    'beta_epsilon': 1e-4,
                    'thresholds': '0:1:0.01',
                    }

            if USEKNOWNFAILURE:
                kwargs['y'] = 50
            else:
                kwargs['y'] = y

            emsg = None
            for i in range(3):
                start = time.time()
                glm = h2o_cmd.runGLMOnly(parseKey=parseKey, timeoutSecs=timeoutSecs, **kwargs)
                print 'glm #', i, 'end on', csvPathname, 'took', time.time() - start, 'seconds'
                # we can pass the warning, without stopping in the test, so we can 
                # redo it in the browser for comparison
                (warnings, coefficients, intercept) = h2o_glm.simpleCheckGLM(self, 
                    glm, None, allowFailWarning=True, **kwargs)

                if 1==0:
                    print "\n", "\ncoefficients in col order:"
                    # since we're loading the x50 file all the time..the real colCount 
                    # should be 50 (0 to 49)
                    if USEKNOWNFAILURE:
                        showCols = 50
                    else:
                        showCols = colCount
                    for c in range(showCols):
                        print "%s:\t%s" % (c, coefficients[c])
                    print "intercept:\t", intercept

                # gets the failed to converge, here, after we see it in the browser too
                x = re.compile("[Ff]ailed")
                if warnings:
                    print "warnings:", warnings
                    for w in warnings:
                        print "w:", w
                        if (re.search(x,w)): 
                            # first
                            if emsg is None: emsg = w
                            print w
                if emsg: break
        
            if not h2o.browse_disable:
                h2b.browseJsonHistoryAsUrlLastMatch("Inspect")
                time.sleep(5)
                h2b.browseJsonHistoryAsUrlLastMatch("GLM")
                time.sleep(5)

            # gets the failed to converge, here, after we see it in the browser too
            if emsg is not None:
                raise Exception(emsg)

if __name__ == '__main__':
    h2o.unit_main()
