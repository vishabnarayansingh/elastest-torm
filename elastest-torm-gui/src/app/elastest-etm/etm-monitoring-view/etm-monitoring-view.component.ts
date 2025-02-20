import { ExternalService } from '../external/external.service';
import { EtmLogsGroupComponent } from './etm-logs-group/etm-logs-group.component';
import { MonitoringConfigurationComponent } from './monitoring-configuration/monitoring-configuration.component';
import { EtmChartGroupComponent } from './etm-chart-group/etm-chart-group.component';
import { Observable, Subject } from 'rxjs/Rx';
import { TJobService } from '../tjob/tjob.service';

import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material';
import { AbstractTJobModel } from '../models/abstract-tjob-model';
import { AbstractTJobExecModel } from '../models/abstract-tjob-exec-model';
import { TJobModel } from '../tjob/tjob-model';
import { ExternalTJobModel } from '../external/external-tjob/external-tjob-model';
import { MonitoringService } from '../../shared/services/monitoring.service';
import { TJobExecModel } from '../tjob-exec/tjobExec-model';
import { LogAnalyzerService } from '../../elastest-log-analyzer/log-analyzer.service';
import { MonitorMarkModel } from './monitor-mark.model';
import { sleep } from '../../shared/utils';
import { LogFieldModel } from '../../shared/logs-view/models/log-field-model';
import { MetricsFieldModel } from '../../shared/metrics-view/metrics-chart-card/models/metrics-field-model';

@Component({
  selector: 'etm-monitoring-view',
  templateUrl: './etm-monitoring-view.component.html',
  styleUrls: ['./etm-monitoring-view.component.scss'],
})
export class EtmMonitoringViewComponent implements OnInit {
  @ViewChild('metricsGroup')
  metricsGroup: EtmChartGroupComponent;
  @ViewChild('logsGroup')
  logsGroup: EtmLogsGroupComponent;

  @Input()
  public live: boolean;

  @Input()
  public showConfigBtn: boolean;

  @Input()
  public hideLogs: boolean = false;

  @Input()
  public hideMetrics: boolean = false;

  tJob: AbstractTJobModel;
  tJobExec: AbstractTJobExecModel;
  isInitialized: boolean = false;
  isMultiParentTJobExec: boolean = false;

  component: string = '';
  stream: string = '';
  metricName: string = '';

  activatedMetrics: MetricsFieldModel[] = [];

  etMonitorMarkPrefix: string = '##elastest-monitor-mark: ';
  lockForMarkPrefix: boolean = false;

  constructor(
    private monitoringService: MonitoringService,
    private externalService: ExternalService,
    private logAnalyzerService: LogAnalyzerService,
    private tJobService: TJobService,
    public dialog: MatDialog,
  ) {}

  ngOnInit(): void {}

  initView(tJob: AbstractTJobModel, tJobExec: AbstractTJobExecModel, customStartDate?: Date, customEndDate?: Date): void {
    this.tJob = tJob;
    this.tJobExec = tJobExec;

    this.isMultiParentTJobExec =
      tJobExec !== undefined &&
      tJobExec.finished() &&
      tJobExec instanceof TJobExecModel &&
      tJobExec.isParent() &&
      tJobExec.execChilds.length > 0;

    // Init Monitor Marks first if there are
    this.initMonitorMarks();

    // If !Multi Parent init directly. Else, init after monitor marks loaded
    if (!this.isMultiParentTJobExec) {
      this.initLogAndMetricViews(this.tJobExec, customStartDate, customEndDate);
    }
  }

  initLogAndMetricViews(
    parentTJobExec: AbstractTJobExecModel = this.tJobExec,
    customStartDate?: Date,
    customEndDate?: Date,
  ): void {
    this.tJobExec = parentTJobExec;

    if (!this.isInitialized) {
      // Load logs
      if (!this.hideLogs) {
        this.logsGroup.initLogsView(this.tJob, this.tJobExec, customStartDate, customEndDate);
      }

      // Load metrics
      if (!this.hideMetrics) {
        this.metricsGroup.initMetricsView(this.tJob, this.tJobExec, undefined, customStartDate, customEndDate);
      }

      this.isInitialized = true;
    }
  }

  timelineEvent($event): void {
    if (this.logsGroup) {
      if (!$event.unselect) {
        this.logsGroup.selectTimeRange($event.domain);
      } else {
        this.logsGroup.unselectTimeRange();
      }
    }
  }

  hoverEvent(time): void {
    if (this.logsGroup) {
      this.logsGroup.selectTracesByTime(time);
    }
  }

  leaveEvent(): void {
    if (this.logsGroup) {
      this.logsGroup.unselectTraces();
    }
  }

  // Adds new monitoring card
  addMore(showPopup: boolean = true, traceType?: 'log' | 'metric'): void {
    this.addMoreSubscribe(traceType).subscribe(
      (obj: any) => {
        let added: boolean = this.addMoreFromObj(obj);
        if (showPopup) {
          if (added) {
            this.monitoringService.popupService.openSnackBar('Added succesfully!', 'OK');
          } else {
            this.monitoringService.popupService.openSnackBar('Already exist', 'OK');
          }
        }
      },
      (error: Error) => console.log(error),
    );
  }

  // Gets data of the new monitoring card to be added
  addMoreSubscribe(traceType?: 'log' | 'metric'): Observable<any> {
    let _addMoreSubject: Subject<any> = new Subject<any>();
    let addMoreObs: Observable<any> = _addMoreSubject.asObservable();

    if (this.isInit()) {
      let monitoringIndex: string = this.tJobExec.monitoringIndex;

      let searchAllObs: Observable<any>;
      // If Multi Parent
      if (this.tJobExec instanceof TJobExecModel && this.tJobExec.isParent()) {
        monitoringIndex = this.tJobExec.getChildsMonitoringIndices();
        searchAllObs = this.monitoringService.searchAllDynamic(
          monitoringIndex,
          this.stream,
          this.component,
          this.metricName,
          this.tJobExec,
          this.tJobExec.startDate,
          this.tJobExec.endDate,
          true,
          true,
          traceType,
        );
      } else {
        searchAllObs = this.monitoringService.searchAllDynamic(
          monitoringIndex,
          this.stream,
          this.component,
          this.metricName,
          undefined,
          this.tJobExec.startDate,
          this.tJobExec.endDate,
          true,
          true,
          traceType,
        );
      }

      searchAllObs.subscribe(
        (obj: any) => {
          _addMoreSubject.next(obj);
        },
        (error: Error) => _addMoreSubject.error('Could not load more: ' + error),
      );
    } else {
      _addMoreSubject.error('Could not load more. EtmMonitoringViewComponent has not been init yet');
    }

    return addMoreObs;
  }

  // Adds data of the new monitoring card to be added
  addMoreFromObj(obj: any): boolean {
    let added: boolean = false;
    if (obj.streamType === 'log') {
      added = this.logsGroup.addMoreLogs(obj);
    } else if (obj.streamType === 'composed_metrics' || obj.streamType === 'atomic_metric') {
      added = this.metricsGroup.addMoreMetrics(obj);
    }

    this.component = '';
    this.stream = '';
    this.metricName = '';

    return added;
  }
  isInit(): boolean {
    return this.tJobExec !== undefined;
  }

  // Persists monitoring config into tJob
  saveMonitoringConfig(showPopup: boolean = true): void {
    switch (this.tJob.getAbstractTJobClass()) {
      case 'TJobModel':
        let tJobModel: TJobModel = this.tJob as TJobModel;
        this.tJobService.modifyTJob(tJobModel).subscribe(
          (tJob: TJobModel) => {
            if (showPopup) {
              this.monitoringService.popupService.openSnackBar('Monitoring configuration saved into TJob', 'OK');
            }
            this.tJob = tJob;
            this.tJobExec.tJob = tJob;
          },
          (error: Error) => console.log(error),
        );
        break;
      case 'ExternalTJobModel':
        let externalTJobModel: ExternalTJobModel = this.tJob as ExternalTJobModel;
        this.externalService.modifyExternalTJob(externalTJobModel).subscribe(
          (exTJob: ExternalTJobModel) => {
            if (showPopup) {
              this.monitoringService.popupService.openSnackBar('Monitoring configuration saved into TJob', 'OK');
            }
            this.tJob = exTJob;
            this.tJobExec.tJob = exTJob;
          },
          (error: Error) => console.log(error),
        );
        break;

      default:
        // Abstract
        break;
    }
  }

  loadLastTraces(): void {
    this.logsGroup.loadLastTraces();
    this.metricsGroup.loadLastTraces();
  }

  public openMonitoringConfig(): void {
    let combineMetricsInPairs: boolean = this.tJob.execDashboardConfigModel.combineMetricsInPairs;
    let dialogRef: MatDialogRef<MonitoringConfigurationComponent> = this.dialog.open(MonitoringConfigurationComponent, {
      data: {
        exec: this.tJobExec,
        logCards: this.logsGroup,
        metricCards: this.metricsGroup,
        hideLogs: this.hideLogs,
        hideMetrics: this.hideMetrics,
        combineMetricsInPairs: combineMetricsInPairs !== undefined ? combineMetricsInPairs : false,
      },
      height: '80%',
      width: '90%',
    });
    dialogRef.afterClosed().subscribe((data: any) => {
      if (data) {
        let withSave: boolean = false;
        let msg: string = 'Monitoring changes has been applied';
        if (data.withSave) {
          withSave = data.withSave;
          msg += ' and saved';
        }
        if (data.logsList) {
          this.updateLogsFromList(data.logsList);
        }
        if (data.logsToCompare) {
          this.updateLogsToCompareFromList(data.logsToCompare);
        }
        if (data.metricsList) {
          this.updateMetricsFromList(data.metricsList);
          this.tJob.execDashboardConfigModel.combineMetricsInPairs = data.combineMetricsPairs;

          if (data.combineMetricsPairs && this.activatedMetrics) {
            this.metricsGroup.initMetricsPairs(this.activatedMetrics);
          }
        }

        if (data.allInOneMetricsActivated !== undefined && data.allInOneMetricsActivated !== null) {
          this.updateAIOMetrics(data.allInOneMetricsActivated);
        }

        if (withSave) {
          this.saveMonitoringConfig(false);
        }

        this.monitoringService.popupService.openSnackBar(msg);
      }
    });
  }

  updateLogsFromList(logsList: any[]): void {
    for (let log of logsList) {
      if (log.activated) {
        this.updateLog(log);
      } else {
        // Disable from tjob object before save
        let logField: LogFieldModel = new LogFieldModel(log.component, log.stream);
        this.tJob.execDashboardConfigModel.allLogsTypes.disableLogField(logField.name, logField.component, logField.stream);
        // Remove
        this.removeLogCard(log);
      }
    }
  }

  public updateLog(log: any, showPopup: boolean = false): void {
    this.component = log.component;
    this.stream = log.stream;
    this.metricName = '';
    // Enable in tjob object before save
    let logField: LogFieldModel = new LogFieldModel(log.component, log.stream);
    this.tJob.execDashboardConfigModel.allLogsTypes.addLogFieldToList(logField.name, logField.component, logField.stream, true);

    this.addMore(showPopup, 'log');
  }

  removeLogCard(log: any): void {
    let position: number = 0;
    for (let logCard of this.logsGroup.logsList) {
      if (logCard.component === log.component && logCard.stream === log.stream) {
        this.logsGroup.removeAndUnsubscribe(position);
        break;
      }
      position++;
    }
  }

  updateLogsToCompareFromList(logsList: any[]): void {
    for (let log of logsList) {
      if (log.activated) {
        this.updateLogToCompare(log);
      } else {
        // Disable from tjob object before save
        let logField: LogFieldModel = new LogFieldModel(log.component, log.stream);
        this.tJob.execDashboardConfigModel.allLogsTypes.disableLogField(logField.name, logField.component, logField.stream);
        // Remove
        this.removeLogComparisonTab(logField);
      }
    }
  }

  public updateLogToCompare(log: any, showPopup: boolean = false): void {
    this.component = log.component;
    this.stream = log.stream;
    this.metricName = '';

    // Enable in tjob object before save
    let logField: LogFieldModel = new LogFieldModel(log.component, log.stream);
    this.tJob.execDashboardConfigModel.allLogsTypes.addLogFieldToList(logField.name, logField.component, logField.stream, true);

    let added: boolean = this.logsGroup.addMoreLogsComparisons(this.tJobExec, logField.name, this.stream, this.component);

    if (showPopup) {
      if (added) {
        this.monitoringService.popupService.openSnackBar('Added succesfully!', 'OK');
      } else {
        this.monitoringService.popupService.openSnackBar('Already exist', 'OK');
      }
    }
  }

  removeLogComparisonTab(logField: LogFieldModel): void {
    this.logsGroup.removeLogComparatorTab(logField);
  }

  updateMetricsFromList(metricsList: any[]): void {
    this.activatedMetrics = [];

    // First, remove all metrics pairs cards
    this.metricsGroup.removeAllMetricsPairs();

    for (let metric of metricsList) {
      if (metric.activated) {
        this.updateMetric(metric);
      } else {
        // Disable from tjob object before save
        let metricField: MetricsFieldModel = new MetricsFieldModel(
          metric.etType,
          metric.subtype,
          metric.unit,
          metric.component,
          metric.stream,
          metric.streamType,
        );

        this.tJob.execDashboardConfigModel.allMetricsFields.disableMetricFieldByTitleName(metricField.name);
        // Remove
        this.removeMetricCard(metric);
      }
    }
  }

  updateMetric(metric: any, showPopup: boolean = false): void {
    this.component = metric.component;
    this.stream = metric.stream;
    this.metricName = metric.metricName;

    // Enable in tjob object before save

    let metricField: MetricsFieldModel = new MetricsFieldModel(
      metric.etType,
      metric.subtype,
      metric.unit,
      metric.component,
      metric.stream,
      metric.streamType,
      true,
    );
    metricField.metricName = this.metricName;
    this.activatedMetrics.push(metricField);

    this.tJob.execDashboardConfigModel.allMetricsFields.addMetricsFieldToList(
      metricField,
      metric.component,
      metric.stream,
      metric.streamType,
      true,
    );

    this.addMore(showPopup, 'metric');
  }

  removeMetricCard(metric: any): void {
    let position: number = 0;
    for (let metricCard of this.metricsGroup.metricsList) {
      if (metricCard.component === metric.component && metricCard.stream === metric.stream) {
        this.metricsGroup.removeAndUnsubscribe(position);
        break;
      }
      position++;
    }
  }

  updateAIOMetrics(activate: boolean): void {
    let alreadyActivated: boolean = this.tJob.execDashboardConfigModel.showAllInOne;
    this.tJob.execDashboardConfigModel.showAllInOne = activate;

    if (this.tJobExec.tJob) {
      this.tJobExec.tJob.execDashboardConfigModel.showAllInOne = activate;
    }

    if (activate && !alreadyActivated) {
      this.metricsGroup.initAIO();
    } else {
      this.metricsGroup.removeAndUnsubscribeAIO();
    }
  }

  /* ********************** */
  /* *** Monitor Marks ***  */
  /* ********************** */

  waitForUnlock(functionsToExec: Function[], parentTJobExec: AbstractTJobExecModel): void {
    // sleep
    sleep(500)
      .then(() => {
        if (this.lockForMarkPrefix) {
          this.waitForUnlock(functionsToExec, parentTJobExec);
        } else {
          if (functionsToExec.length > 0) {
            functionsToExec.shift()();
            this.waitForUnlock(functionsToExec, parentTJobExec);
          } else {
            this.initLogAndMetricViews(parentTJobExec);
          }
        }
      })
      .catch((e) => {
        this.waitForUnlock(functionsToExec, parentTJobExec);
      });
  }

  initMonitorMarks(tJobExec: AbstractTJobExecModel = this.tJobExec): AbstractTJobExecModel {
    if (tJobExec !== undefined && tJobExec.finished()) {
      if (tJobExec instanceof TJobExecModel && tJobExec.isParent() && tJobExec.execChilds.length > 0) {
        let childPos: number = 0;
        let functionsToExec: Function[] = [];
        for (let child of tJobExec.execChilds) {
          let currentChildPos: number = childPos;
          functionsToExec.push(() => {
            tJobExec.execChilds[currentChildPos] = this.initMonitorMarks(child) as TJobExecModel;
          });

          childPos++;
        }
        this.waitForUnlock(functionsToExec, tJobExec);
      } else {
        this.lockForMarkPrefix = true;
        this.logAnalyzerService
          .searchTraceByGivenMsg(
            this.etMonitorMarkPrefix,
            tJobExec.getMonitoringIndexAsList(),
            tJobExec.startDate,
            tJobExec.endDate,
          )
          .subscribe(
            (monitorMarkTraces: any[]) => {
              for (let markTrace of monitorMarkTraces) {
                let msg: string = markTrace.message;
                let timestamp: string = markTrace['@timestamp'];
                let markModel: MonitorMarkModel = new MonitorMarkModel();
                markModel.initByGivenMsg(msg, timestamp);

                if (!markModel.isEmpty()) {
                  // Use addMonitoringMark instead of push
                  tJobExec.addMonitoringMark(markModel);
                }
              }
              this.lockForMarkPrefix = false;
            },
            (error: Error) => {
              console.log(error);
              this.lockForMarkPrefix = false;
            },
          );
      }

      if (tJobExec instanceof TJobExecModel && tJobExec.isChild()) {
        // Do nothing
      } else {
        this.tJobExec = tJobExec;
      }
      return tJobExec;
    }
  }

  getLogsErrors(): number {
    if (this.logsGroup) {
      return this.logsGroup.getErrors();
    } else {
      return 0;
    }
  }

  getLogsWarnings(): number {
    if (this.logsGroup) {
      return this.logsGroup.getWarnings();
    } else {
      return 0;
    }
  }
}
