import { FilesService } from '../../../shared/services/files.service';
import { TdDialogService } from '@covalent/core/dialogs/services/dialog.service';
import { IConfirmConfig } from '@covalent/core';
import { TitlesService } from '../../../shared/services/titles.service';
import { EtmMonitoringViewComponent } from '../../etm-monitoring-view/etm-monitoring-view.component';

import { TJobModel } from '../../tjob/tjob-model';
import { TJobService } from '../../tjob/tjob.service';
import { TJobExecModel } from '../tjobExec-model';
import { TJobExecService } from '../tjobExec.service';

import { Component, OnInit, ViewChild, ViewContainerRef } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { MdDialog } from '@angular/material';
import { MetricTraces, LogTraces, MonitoringService } from '../../../shared/services/monitoring.service';

@Component({
  selector: 'etm-tjob-exec-manager',
  templateUrl: './tjob-exec-manager.component.html',
  styleUrls: ['./tjob-exec-manager.component.scss'],
})
export class TjobExecManagerComponent implements OnInit {
  @ViewChild('logsAndMetrics')
  logsAndMetrics: EtmMonitoringViewComponent;
  showLogsAndMetrics: boolean = false;

  tJobId: number;
  tJobExecId: number;
  tJobExec: TJobExecModel;
  tJob: TJobModel;
  downloading: boolean = false;

  statusIcon: any = {
    name: '',
    color: '',
  };

  // TJob Data
  testCaseColumns: any[] = [
    { name: 'id', label: 'Id' },
    { name: 'name', label: 'Name' },
    { name: 'time', label: 'Time (s)' },
    { name: 'failureMessage', label: 'Failure Msg' },
    { name: 'failureType', label: 'Failure Type' },
    { name: 'failureErrorLine', label: 'Failure Error Line' },
    { name: 'failureDetail', label: 'Failure Detail' },
  ];

  constructor(
    private titlesService: TitlesService,
    private tJobExecService: TJobExecService,
    private tJobService: TJobService,
    private monitoringService: MonitoringService,
    private route: ActivatedRoute,
    private router: Router,
    private _dialogService: TdDialogService,
    private _viewContainerRef: ViewContainerRef,
    private filesService: FilesService,
    public dialog: MdDialog,
  ) {
    if (this.route.params !== null || this.route.params !== undefined) {
      this.route.params.subscribe((params: Params) => {
        this.tJobId = params.tJobId;
        this.tJobExecId = params.tJobExecId;
      });
    }
  }

  ngOnInit() {
    this.tJobExec = new TJobExecModel();
    this.loadTJobExec();
  }

  loadTJobExec(): void {
    this.tJobExecService.getTJobExecutionByTJobId(this.tJobId, this.tJobExecId).subscribe((tJobExec: TJobExecModel) => {
      this.tJobExec = tJobExec;
      this.statusIcon = this.tJobExec.getResultIcon();
      this.titlesService.setHeadTitle('Execution ' + this.tJobExec.id + ' result');
      this.titlesService.setPathName(this.router.routerState.snapshot.url);

      this.tJobService.getTJob(this.tJobId.toString()).subscribe(
        (tJob: TJobModel) => {
          this.tJob = tJob;
          if (!this.tJobExec.finished()) {
            this.router.navigate(['/projects', tJob.project.id, 'tjob', this.tJobId, 'tjob-exec', this.tJobExecId, 'live'], {
              queryParams: { fromTJobManager: true },
            });
          } else if (this.logsAndMetrics) {
            this.logsAndMetrics.initView(this.tJob, this.tJobExec);
            this.showLogsAndMetrics = true;
          }
        },
        (error) => console.log(error),
      );
    });
  }

  viewTJob(): void {
    this.router.navigate(['/projects', this.tJob.project.id, 'tjob', this.tJobId]);
  }

  viewInLogAnalyzer(): void {
    this.router.navigate(['projects', this.tJob.project.id, 'tjob', this.tJob.id, 'tjob-exec', this.tJobExec.id, 'loganalyzer'], {
      queryParams: { tjob: this.tJob.id, exec: this.tJobExec.id },
    });
  }

  deleteTJobExec(): void {
    let iConfirmConfig: IConfirmConfig = {
      message: 'TJob Execution ' + this.tJobExec.id + ' will be deleted, do you want to continue?',
      disableClose: false,
      viewContainerRef: this._viewContainerRef,
      title: 'Confirm',
      cancelButton: 'Cancel',
      acceptButton: 'Yes, delete',
    };
    this._dialogService
      .openConfirm(iConfirmConfig)
      .afterClosed()
      .subscribe((accept: boolean) => {
        if (accept) {
          this.tJobExecService.deleteTJobExecution(this.tJob, this.tJobExec).subscribe(
            (exec) => {
              this.tJobExecService.popupService.openSnackBar(
                'TJob Execution Nº' + this.tJobExec.id + ' has been removed successfully!',
              );
              this.viewTJob();
            },
            (error) => {
              this.tJobExecService.popupService.openSnackBar('TJob Execution could not be deleted');
            },
          );
        }
      });
  }

  downloadAsJson(): void {
    this.downloading = true;
    let jsonObj: object = {
      tJobExec: this.tJobExec,
    };

    try {
      this.tJobExecService.loadTestSuitesInfoToDownload(this.tJobExec, [...this.tJobExec.testSuites]).subscribe(
        (someTestSuiteWithDate: boolean) => {
          if (someTestSuiteWithDate) {
            this.downloading = false;
            jsonObj['tJobExec'] = this.tJobExec;
            this.filesService.downloadObjectAsJson(jsonObj);
          } else {
            this.downloadAsJsonWithoutTestCases();
          }
        },
        (error: Error) => {
          console.log('Error: ' + error, 'Trying to download without testcases');
          this.downloadAsJsonWithoutTestCases();
        },
      );
    } catch (e) {
      this.downloading = false;
      this.monitoringService.popupService.openSnackBar('Error: the execution could not be downloaded as json');
    }
  }

  downloadAsJsonWithoutTestCases(): void {
    let jsonObj: object = {
      tJobExec: this.tJobExec,
    };

    this.monitoringService.getAllTJobExecLogs(this.tJobExec).subscribe(
      (logsTraces: LogTraces[]) => {
        jsonObj['logs'] = logsTraces;

        // Todo metrics and disable btn while processing
        this.monitoringService.getAllTJobExecMetrics(this.tJobExec).subscribe((metricsTraces: MetricTraces[]) => {
          jsonObj['metrics'] = metricsTraces;

          // Create tmp url and link element for download
          this.filesService.downloadObjectAsJson(jsonObj);
          this.downloading = false;
        });
      },
      (error: Error) => {
        this.downloading = false;
        this.monitoringService.popupService.openSnackBar('Error: the execution could not be downloaded as json');
      },
    );
  }

  openExternalUrl(): void {
    let url: string = this.tJobExec.getExternalUrl();
    if (url !== undefined) {
      window.open(url);
    }
  }
}
