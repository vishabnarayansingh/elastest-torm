import { MultiConfigModel } from '../../shared/multi-config-view/multi-config-view.component';
import { PopupService } from '../../shared/services/popup.service';
import { ETModelsTransformServices } from '../../shared/services/et-models-transform.service';
import { ConfigurationService } from '../../config/configuration-service.service';
import { TJobModel } from '../tjob/tjob-model';
import { TJobExecModel } from './tjobExec-model';
import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs/Rx';
import 'rxjs/Rx';
import { TestCaseModel } from '../test-case/test-case-model';
import { TestSuiteModel } from '../test-suite/test-suite-model';
import { LogAnalyzerService, StartFinishTestCaseTraces } from '../../elastest-log-analyzer/log-analyzer.service';
import { MetricTraces } from '../../shared/services/monitoring.service';
import { sleep } from '../../shared/utils';
import { HttpClient, HttpResponse } from '@angular/common/http';

@Injectable()
export class TJobExecService {
  constructor(
    private http: HttpClient,
    private configurationService: ConfigurationService,
    private eTModelsTransformServices: ETModelsTransformServices,
    public popupService: PopupService,
    public logAnalyzerService: LogAnalyzerService,
  ) {}

  //  TJobExecution functions
  public runTJob(
    tJobId: number,
    parameters?: any[],
    sutParams?: any[],
    multiConfigs?: MultiConfigModel[],
  ): Observable<TJobExecModel> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/' + tJobId + '/exec';
    let body: any = {};
    if (parameters) {
      body['tJobParams'] = parameters;
    }

    if (sutParams) {
      body['sutParams'] = sutParams;
    }

    if (multiConfigs) {
      body['multiConfigurations'] = multiConfigs;
    }

    return this.http.post(url, body, { observe: 'response' }).map((response: HttpResponse<any>) => {
      let data: any = undefined;
      try {
        data = response.body;
      } catch (e) {}
      if (response.status !== 200) {
        let msg: string = 'Sut instrumented by Elastest is still activating beats. Wait and try again';
        this.popupService.openSnackBar(response.status + ' Code: ' + msg, 'OK', 5000);
        throw new Error(msg);
      } else {
        if (data !== undefined && data !== null) {
          return this.eTModelsTransformServices.jsonToTJobExecModel(data);
        } else {
          throw new Error('Empty response.');
        }
      }
    });
  }

  // Get all Executions of a TJob
  public getTJobsExecutions(tJob: TJobModel): Observable<TJobExecModel[]> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/' + tJob.id + '/exec';
    return this.http.get(url).map((data: any[]) => this.eTModelsTransformServices.jsonToTJobExecsList(data));
  }

  public getTJobsExecutionsWithoutChilds(tJob: TJobModel): Observable<TJobExecModel[]> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/' + tJob.id + '/exec/withoutchilds';
    return this.http.get(url).map((data: any[]) => this.eTModelsTransformServices.jsonToTJobExecsList(data));
  }

  public getTJobExecutionFiles(tJobId: number, tJobExecId: number): Observable<any> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/' + tJobId + '/exec/' + tJobExecId + '/files';
    return this.http.get(url);
  }

  /*public getTJobExecutionFiles(tJobExec: TJobExecModel){
    let url: string = this.configurationService.configModel.hostApi + '/tjob/' + tJobExec.tJob.id + '/exec/' + tJobExec.id + '/files';
    return this.http.get(url)
    .map((data: any[]) => console.log(data));
  }*/

  // Get a specific TJob Execution
  public getTJobExecution(tJob: TJobModel, idTJobExecution: number | string): Observable<TJobExecModel> {
    return this.getTJobExecutionByTJobId(tJob.id, idTJobExecution);
  }

  public getLastNTJobExecutions(tJobId: number | string, n: number, withoutChilds: boolean): Observable<TJobExecModel[]> {
    let url: string =
      this.configurationService.configModel.hostApi + '/tjob/' + tJobId + '/execs/last/' + n + '?withoutChilds=' + withoutChilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  public getTJobExecsPageSinceId(
    tJobId: number,
    page: number,
    pageSize: number,
    sortOrder: 'desc' | 'asc',
    withoutchilds: boolean = false,
  ): Observable<TJobExecModel[]> {
    let url: string =
      this.configurationService.configModel.hostApi +
      '/tjob/' +
      tJobId +
      '/exec/range' +
      '?page=' +
      page +
      '&pageSize=' +
      pageSize +
      '&sortOrder=' +
      sortOrder +
      '&withoutChilds=' +
      withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  /* ****************************** */
  /* *** All execs of all TJobs *** */
  /* ****************************** */

  public getAllTJobExecs(): Observable<TJobExecModel[]> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/execs';
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  /* *** By ID *** */

  public getTJobsExecsRange(page: number, pageSize: number, withoutchilds: boolean = false): Observable<TJobExecModel[]> {
    let url: string =
      this.configurationService.configModel.hostApi +
      '/tjob/execs/range' +
      '?page=' +
      page +
      '&pageSize=' +
      pageSize +
      '&withoutChilds=' +
      withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  public getLastNTJobsExecutions(n: number, withoutchilds: boolean = false): Observable<TJobExecModel[]> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/execs/last/' + n + '?withoutChilds=' + withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  /* *** By results *** */

  public getAllRunningTJobsExecutions(withoutchilds: boolean = false): Observable<TJobExecModel[]> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/execs/running' + '?withoutChilds=' + withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  public getRunningTJobsExecutionsByPage(
    page: number,
    pageSize: number,
    withoutchilds: boolean = false,
  ): Observable<TJobExecModel[]> {
    let url: string =
      this.configurationService.configModel.hostApi +
      '/tjob/execs/running/range' +
      '?page=' +
      page +
      '&pageSize=' +
      pageSize +
      '&withoutChilds=' +
      withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  public getLastNRunningTJobsExecutions(n: number, withoutchilds: boolean = false): Observable<TJobExecModel[]> {
    let url: string =
      this.configurationService.configModel.hostApi + '/tjob/execs/running/last/' + n + '?withoutChilds=' + withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  public getAllFinishedOrNotExecutedTJobsExecutions(withoutchilds: boolean = false): Observable<TJobExecModel[]> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/execs/finished' + '?withoutChilds=' + withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  public getAllFinishedOrNotExecutedTJobsExecutionsSinceId(
    execId: number | string,
    than: 'less' | 'greater',
    withoutchilds: boolean = false,
  ): Observable<TJobExecModel[]> {
    let url: string =
      this.configurationService.configModel.hostApi +
      '/tjob/execs/finished/' +
      execId +
      '?than=' +
      than +
      '&withoutChilds=' +
      withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  public getFinishedOrNotExecutedTJobsExecutionsByPage(
    page: number,
    pageSize: number,
    withoutchilds: boolean = false,
  ): Observable<TJobExecModel[]> {
    let url: string =
      this.configurationService.configModel.hostApi +
      '/tjob/execs/finished/range' +
      '?page=' +
      page +
      '&pageSize=' +
      pageSize +
      '&withoutChilds=' +
      withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  public getFinishedOrNotExecutedTJobsExecutionsByPageAndSinceId(
    execId: number | string,
    page: number,
    pageSize: number,
    than: 'less' | 'greater',
    withoutchilds: boolean = false,
  ): Observable<TJobExecModel[]> {
    let url: string =
      this.configurationService.configModel.hostApi +
      '/tjob/execs/finished/range/' +
      execId +
      '?page=' +
      page +
      '&pageSize=' +
      pageSize +
      '&than=' +
      than +
      '&withoutChilds=' +
      withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  public getLastNFinishedOrNotExecutedTJobsExecutions(n: number, withoutchilds: boolean = false): Observable<TJobExecModel[]> {
    let url: string =
      this.configurationService.configModel.hostApi + '/tjob/execs/finished/last/' + n + '?withoutChilds=' + withoutchilds;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. There are not TJobExecutions or you don't have permissions to access them");
      }
    });
  }

  public getTJobExecutionByTJobId(tJobId: number, idTJobExecution: number | string): Observable<TJobExecModel> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/' + tJobId + '/exec/' + idTJobExecution;
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecModel(data);
      } else {
        throw new Error("Empty response. TJob Execution not exist or you don't have permissions to access it");
      }
    });
  }

  public stopTJobExecution(tJob: TJobModel, tJobExecution: TJobExecModel): Observable<TJobExecModel> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/' + tJob.id + '/exec/' + tJobExecution.id + '/stop';
    return this.http.delete(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecModel(data);
      } else {
        throw new Error("Empty response. TJob Execution not exist or you don't have permissions to access it");
      }
    });
  }

  public deleteTJobExecution(tJob: TJobModel, tJobExecution: TJobExecModel): Observable<any> {
    return this.deleteTJobExecutionById(tJob, tJobExecution.id);
  }

  public deleteTJobExecutionById(tJob: TJobModel, tJobExecutionId: number | string): Observable<any> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/' + tJob.id + '/exec/' + tJobExecutionId;
    return this.http.delete(url);
  }

  public getResultStatus(tJobId: string | number, tJobExecution: TJobExecModel): Observable<any> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/' + tJobId + '/exec/' + tJobExecution.id + '/result';
    return this.http.get(url);
  }

  public getResultStatusByTJob(tJob: TJobModel, tJobExecution: TJobExecModel): Observable<any> {
    return this.getResultStatus(tJob.id, tJobExecution);
  }

  public getChildTJobExecParent(tJobExecutionId: string | number): Observable<TJobExecModel> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/exec/' + tJobExecutionId + '/parent';
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecModel(data);
      } else {
        throw new Error("Empty response. TJob Execution not exist or you don't have permissions to access it");
      }
    });
  }

  public getParentTJobExecChilds(tJobExecutionId: string | number): Observable<TJobExecModel[]> {
    let url: string = this.configurationService.configModel.hostApi + '/tjob/exec/' + tJobExecutionId + '/childs';
    return this.http.get(url).map((data: any) => {
      if (data !== undefined && data !== null) {
        return this.eTModelsTransformServices.jsonToTJobExecsList(data);
      } else {
        throw new Error("Empty response. TJob Execution not exist or you don't have permissions to access it");
      }
    });
  }

  /* ************* */
  /* *** Utils *** */
  /* ************* */

  loadTestSuitesInfoToDownload(tJobExec: TJobExecModel, testSuites: TestSuiteModel[]): Observable<boolean> {
    let _suites: Subject<boolean> = new Subject<boolean>();
    let suitesObs: Observable<boolean> = _suites.asObservable();
    this.loadTestSuitesInfoToDownloadByGiven(tJobExec, testSuites, _suites, false);
    return suitesObs;
  }

  loadTestSuitesInfoToDownloadByGiven(
    tJobExec: TJobExecModel,
    testSuites: TestSuiteModel[],
    _suites: Subject<boolean>,
    someTestSuiteWithDate: boolean,
  ): void {
    if (testSuites.length > 0) {
      let suite: TestSuiteModel = testSuites.shift();
      this.loadTestCasesInfoToDownload(tJobExec, [...suite.testCases]).subscribe(
        (someTestCaseWithDate: boolean) => {
          someTestSuiteWithDate = someTestSuiteWithDate || someTestCaseWithDate;

          this.loadTestSuitesInfoToDownloadByGiven(tJobExec, testSuites, _suites, someTestSuiteWithDate);
        },
        (error: Error) => {
          _suites.error(error);
        },
      );
    } else {
      sleep(1000).then(() => {
        // because sometimes, next is fired before subscription
        _suites.next(someTestSuiteWithDate);
      });
    }
  }

  loadTestCasesInfoToDownload(tJobExec: TJobExecModel, testCases: TestCaseModel[]): Observable<boolean> {
    let _cases: Subject<boolean> = new Subject<boolean>();
    let casesObs: Observable<boolean> = _cases.asObservable();
    this.loadTestCasesInfoToDownloadByGiven(tJobExec, testCases, _cases, false);
    return casesObs;
  }

  loadTestCasesInfoToDownloadByGiven(
    tJobExec: TJobExecModel,
    testCases: TestCaseModel[],
    _cases: Subject<boolean>,
    someTestCaseWithDate: boolean,
  ): void {
    if (testCases.length > 0) {
      let tCase: TestCaseModel = testCases.shift();

      // Obtain start/finish traces first
      this.logAnalyzerService
        .searchTestCaseStartAndFinishTraces(tCase.name, [tJobExec.monitoringIndex], tJobExec.startDate, tJobExec.endDate)
        .subscribe(
          (startFinishObj: StartFinishTestCaseTraces) => {
            let _logs: Subject<any[]> = new Subject<any[]>();
            let logsObs: Observable<any[]> = _logs.asObservable();

            // Logs
            this.logAnalyzerService.searchTestCaseLogsByGivenStartFinishTraces(
              tCase.name,
              [tJobExec.monitoringIndex],
              startFinishObj,
              _logs,
            );

            logsObs.subscribe(
              (logs: any[]) => {
                someTestCaseWithDate = true;
                tCase['logs'] = logs;

                // Metrics
                this.logAnalyzerService.monitoringService.getAllTJobExecMetrics(tJobExec).subscribe(
                  (metricsTraces: MetricTraces[]) => {
                    tCase['metrics'] = metricsTraces;

                    this.loadTestCasesInfoToDownloadByGiven(tJobExec, testCases, _cases, someTestCaseWithDate);
                  },
                  (error: Error) => {
                    this.loadTestCasesInfoToDownloadByGiven(tJobExec, testCases, _cases, someTestCaseWithDate);
                  },
                );
              },
              (error: Error) => {
                this.loadTestCasesInfoToDownloadByGiven(tJobExec, testCases, _cases, someTestCaseWithDate);
              },
            );
          },
          (error: Error) => {
            _cases.error(error);
          },
        );
    } else {
      _cases.next(someTestCaseWithDate);
    }
  }
}
