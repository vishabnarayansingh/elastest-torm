import { Component, OnInit, ViewChild, Input } from '@angular/core';
import { ExternalDataModel } from '../../../models/external-data-model';
import { IExternalExecution } from '../../../models/external-execution-interface';
import { ExternalService } from '../../../external.service';
import { ExternalTJobModel } from '../../../external-tjob/external-tjob-model';
import { Observable } from 'rxjs/Observable';
import { ServiceType } from '../../../external-project/external-project-model';
import { BuildModel } from '../../../../../etm-testlink/models/build-model';
import { TestLinkService } from '../../../../../etm-testlink/testlink.service';
import { TLTestCaseModel } from '../../../../../etm-testlink/models/test-case-model';
import { ExternalTestCaseModel } from '../../../external-test-case/external-test-case-model';
import { ExternalTJobExecModel } from '../../external-tjob-execution-model';
import { window } from 'rxjs/operator/window';
import { ExternalTestExecutionModel } from '../../../external-test-execution/external-test-execution-model';

@Component({
  selector: 'etm-case-execution-view',
  templateUrl: './case-execution-view.component.html',
  styleUrls: ['./case-execution-view.component.scss'],
})
export class CaseExecutionViewComponent implements OnInit, IExternalExecution {
  @ViewChild('executionForm') executionForm: IExternalExecution;

  @Input() exTJob: ExternalTJobModel;
  @Input() exTJobExec: ExternalTJobExecModel;

  serviceType: ServiceType;
  externalTestCases: ExternalTestCaseModel[] = [];
  currentExternalTestExecution: ExternalTestExecutionModel;

  data: any;
  tJobExecUrl: string;

  // TestLink
  showTestLinkExec: boolean = false;
  testLinkBuilds: BuildModel[] = [];
  testLinkSelectedBuild: BuildModel;
  disableTLNextBtn: boolean = false;
  execFinished: boolean = false;

  constructor(private externalService: ExternalService, private testLinkService: TestLinkService) {}
  ngOnInit() {
    this.serviceType = this.exTJob.getServiceType();
    this.initExecutionView();
  }

  initExecutionView(): void {
    if (this.exTJobExec) {
      this.tJobExecUrl =
        document.location.origin +
        '/#/external/project/' +
        this.exTJob.exProject.id +
        '/tjob/' +
        this.exTJob.id +
        '/exec/' +
        this.exTJobExec.id;
      this.tJobExecUrl = ' <p><a href="' + this.tJobExecUrl + '">' + this.tJobExecUrl + '</a></p>';
    }

    switch (this.serviceType) {
      case 'TESTLINK':
        this.initTestLinkData();
        break;
      default:
        break;
    }
  }

  /**********************/
  /* **** TestLink **** */
  /**********************/

  initTestLinkData(): void {
    this.testLinkService.getPlanBuildsById(this.exTJob.externalId).subscribe(
      (builds: BuildModel[]) => {
        this.testLinkBuilds = builds;
      },
      (error) => console.log(error),
    );
  }

  selectTestLinkBuild(): void {
    this.showTestLinkExec = true;
    this.externalTestCases = this.exTJob.exTestCases;
    if (this.testLinkSelectedBuild) {
      this.testLinkService.getBuildById(this.testLinkSelectedBuild.id).subscribe(
        (build: BuildModel) => {
          this.data = {
            build: build,
            additionalNotes: this.tJobExecUrl,
          };
          // Load First TCase
          this.loadNextTestLinkCase();
        },
        (error) => console.log(error),
      );
    }
  }

  loadNextTestLinkCase(): void {
    let nextCase: ExternalTestCaseModel = this.externalTestCases.shift();
    if (nextCase !== undefined) {
      // this.currentExternalTestExecution = new ExternalTestExecutionModel();
      // this.currentExternalTestExecution.exTestCase = nextCase;
      // this.currentExternalTestExecution.exTJobExec = this.exTJobExec;
      // this.currentExternalTestExecution.startDate = new Date();
      // this.currentExternalTestExecution.externalSystemId = nextCase.externalSystemId;
      // this.currentExternalTestExecution.externalId = 'executing-' + nextCase.id+'-'+;
      // this.externalService.createExternalTestExecution(this.currentExternalTestExecution).subscribe(
      //   (savedExTestExec: ExternalTestExecutionModel) => {
      //     this.startTestLinkTestCaseExecution(nextCase.externalId);
      //   },
      //   (error) => console.log(error),
      // );
      this.startTestLinkTestCaseExecution(nextCase.externalId);

    } else {
      this.finishTJobExecution();
    }
  }

  startTestLinkTestCaseExecution(testCaseId: string): void {
    this.testLinkService.getTestCaseById(testCaseId).subscribe(
      (testCase: TLTestCaseModel) => {
        // New object to detect on changes
        this.data = {
          testCase: testCase,
          build: this.data.build,
          additionalNotes: this.tJobExecUrl,
        };
      },
      (error) => console.log(error),
    );
  }

  saveTLCaseExecution(): void {
    this.saveExecution().subscribe(
      (saved: boolean) => {
        this.externalService.popupService.openSnackBar('TestCase Execution has been saved successfully');
        this.loadNextTestLinkCase();
      },
      (error) => console.log(error),
    );
  }

  finishTJobExecution(): void {
    this.disableTLNextBtn = true;
    this.execFinished = true;
    this.externalService.getExternalTestExecsByExternalTJobExecId(this.exTJobExec.id).subscribe(
      (exTestExecs: ExternalTestExecutionModel[]) => {
        this.exTJobExec.exTestExecs = exTestExecs;
        this.exTJobExec.updateResultByTestExecsResults();
        this.externalService.popupService.openSnackBar('There is no more Test Cases to Execute');
        this.exTJobExec.endDate = new Date();
        this.exTJobExec.exTestExecs = []; // TODO fix No _valueDeserializer assigned
        this.externalService.modifyExternalTJobExec(this.exTJobExec).subscribe();
      },
      (error) => {
        this.exTJobExec.result = 'SUCCESS';
        this.externalService.popupService.openSnackBar('There is no more Test Cases to Execute');
        this.externalService.modifyExternalTJobExec(this.exTJobExec).subscribe();
      },
    );
  }

  saveExecution(): Observable<boolean> {
    if (this.executionForm) {
      return this.executionForm.saveExecution();
    } else {
      this.finishTJobExecution();
      throw new Error('Error: TestCase View not loaded');
    }
  }

  forceStop(): Observable<ExternalTJobExecModel> {
    this.exTJobExec.result = 'STOPPED';
    return this.externalService.modifyExternalTJobExec(this.exTJobExec);
  }
}
