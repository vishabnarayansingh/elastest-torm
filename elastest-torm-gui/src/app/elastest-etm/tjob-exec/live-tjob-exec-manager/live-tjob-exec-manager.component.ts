import { PopupService } from '../../../shared/services/popup.service';
import { ConfigurationService } from '../../../config/configuration-service.service';
import { TitlesService } from '../../../shared/services/titles.service';
import { EsmServiceInstanceModel } from '../../../elastest-esm/esm-service-instance.model';
import { EsmService } from '../../../elastest-esm/esm-service.service';
import { EtmMonitoringViewComponent } from '../../etm-monitoring-view/etm-monitoring-view.component';
import { TJobModel } from '../../tjob/tjob-model';
import { AfterViewInit, Component, ViewChild, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Subscription, Observable } from 'rxjs/Rx';

import { ElastestRabbitmqService } from '../../../shared/services/elastest-rabbitmq.service';
import { TJobExecModel } from '../../tjob-exec/tjobExec-model';
import { TJobExecService } from '../../tjob-exec/tjobExec.service';
import { TJobService } from '../../tjob/tjob.service';
import { ParameterModel } from '../../parameter/parameter-model';
import { interval } from 'rxjs';

@Component({
  selector: 'etm-live-tjob-exec-manager',
  templateUrl: './live-tjob-exec-manager.component.html',
  styleUrls: ['./live-tjob-exec-manager.component.scss'],
})
export class LiveTjobExecManagerComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('logsAndMetrics')
  logsAndMetrics: EtmMonitoringViewComponent;
  showLogsAndMetrics: boolean = false;

  elastestMode: string;

  tJobId: number;
  tJob: TJobModel;
  withSut: boolean = false;

  tJobExecId: number;
  tJobExec: TJobExecModel;

  serviceInstances: EsmServiceInstanceModel[] = [];
  instancesNumber: number;
  eusInstance: EsmServiceInstanceModel;

  tJobExecMultiConfigs: ParameterModel[] = [];
  tJobExecParameters: ParameterModel[] = [];

  statusMessage: string = '';
  statusIcon: any = {
    name: '',
    color: '',
  };

  disableStopBtn: boolean = false;

  checkResultSubscription: Subscription;
  checkTSSInstancesSubscription: Subscription;

  constructor(
    private titlesService: TitlesService,
    private tJobService: TJobService,
    private tJobExecService: TJobExecService,
    private elastestRabbitmqService: ElastestRabbitmqService,
    private route: ActivatedRoute,
    private router: Router,
    private popupService: PopupService,
    private esmService: EsmService,
    private configurationService: ConfigurationService,
  ) {
    this.elastestMode = this.configurationService.configModel.elasTestExecMode;
  }

  ngOnInit(): void {
    this.router.routeReuseStrategy.shouldReuseRoute = function() {
      return false;
    };

    if (this.route.params !== null || this.route.params !== undefined) {
      this.route.params.subscribe((params: Params) => {
        this.tJobId = params.tJobId;
        this.tJobExecId = params.tJobExecId;
        this.setTitle();
      });
    }
  }

  ngAfterViewInit(): void {
    this.loadTJobExec();
  }

  ngOnDestroy(): void {
    this.elastestRabbitmqService.unsubscribeWSDestination();
    this.unsubscribeCheckResult();
    this.unsubscribeCheckTssInstances();
  }

  setTitle(): void {
    let title: string = 'Live TJob Execution';

    if (this.tJobExec) {
      if (this.tJobExec.isChild()) {
        title = 'Live Configuration Execution';
      } else if (this.tJobExec.isParent()) {
        title = 'Live Multi-Config Execution';
      }
    }
    title += this.tJobExecId !== undefined ? ' ' + this.tJobExecId : '';
    this.titlesService.setHeadTitle(title);
  }

  loadTJobExec(): void {
    this.tJobExecService.getTJobExecutionByTJobId(this.tJobId, this.tJobExecId).subscribe((tJobExec: TJobExecModel) => {
      this.tJobExec = tJobExec;
      this.setTitle();
      this.titlesService.setPathName(this.router.routerState.snapshot.url);
      this.withSut = this.tJobExec.tJob.hasSut();

      if (this.tJobExec.parameters) {
        for (let param of this.tJobExec.parameters) {
          let parameter: ParameterModel = new ParameterModel(param);
          if (param.multiConfig) {
            this.tJobExecMultiConfigs.push(parameter);
          } else {
            this.tJobExecParameters.push(parameter);
          }
        }
      }

      this.tJobService.getTJob(this.tJobExec.tJob.id.toString()).subscribe((tJob: TJobModel) => {
        this.tJob = tJob;
        if (!this.tJobExec.finished()) {
          this.checkResultStatus();
          this.instancesNumber = this.tJobExec.tJob.esmServicesChecked;
          if (tJobExec) {
            this.getSupportServicesInstances();

            if (this.tJobExec.isChild()) {
              this.tJobExecService.getChildTJobExecParent(this.tJobExec.id).subscribe(
                (parent: TJobExecModel) => {
                  this.tJobExec.execParent = parent;
                },
                (error: Error) => console.log(error),
              );
            }
          }
          if (this.logsAndMetrics) {
            this.logsAndMetrics.initView(tJob, this.tJobExec);
            this.showLogsAndMetrics = true;
            if (!this.tJobExec.starting()) {
              // If it's already started, get last trace(s)
              this.logsAndMetrics.loadLastTraces();
            }
          }
        }
      });
    });
  }

  navigateToResultPage(): void {
    this.router.navigate(['/projects', this.tJob.project.id, 'tjob', this.tJobId, 'tjob-exec', this.tJobExecId]);
  }

  getSupportServicesInstances(): void {
    let timer: Observable<number> = interval(2200);
    if (this.checkTSSInstancesSubscription === null || this.checkTSSInstancesSubscription === undefined) {
      this.checkTSSInstancesSubscription = timer.subscribe(() => {
        this.esmService.getSupportServicesInstancesByTJobExec(this.tJobExec).subscribe(
          (serviceInstances: EsmServiceInstanceModel[]) => {
            if (serviceInstances.length === this.instancesNumber || this.tJobExec.finished()) {
              this.unsubscribeCheckTssInstances();
              this.serviceInstances = [...serviceInstances];
              this.searchForEUS();
            }
          },
          (error: Error) => console.log(error),
        );
      });
    }
  }

  searchForEUS(): void {
    if (this.serviceInstances && this.serviceInstances.length > 0) {
      for (let instance of this.serviceInstances) {
        if (instance && instance.serviceName && instance.serviceName.toLowerCase() === 'eus') {
          this.eusInstance = instance;
          break;
        }
      }
    }
  }

  unsubscribeCheckTssInstances(): void {
    if (this.checkTSSInstancesSubscription !== undefined) {
      this.checkTSSInstancesSubscription.unsubscribe();
      this.checkTSSInstancesSubscription = undefined;
    }
  }

  checkResultStatus(): void {
    let timer: Observable<number> = interval(1800);
    if (this.checkResultSubscription === null || this.checkResultSubscription === undefined) {
      this.checkResultSubscription = timer.subscribe(() => {
        this.tJobExecService.getResultStatusByTJob(this.tJob, this.tJobExec).subscribe(
          (data) => {
            if (data.result !== this.tJobExec.result) {
              this.tJobExec.result = data.result;
            }
            this.tJobExec.resultMsg = data.msg;
            if (this.tJobExec.finished()) {
              this.unsubscribeCheckResult();
              this.tJobExecService
                .getTJobExecutionByTJobId(this.tJobId, this.tJobExecId)
                .subscribe((finishedTJobExec: TJobExecModel) => {
                  this.tJobExec = finishedTJobExec;
                  this.statusIcon = this.tJobExec.getResultIcon();
                });
              this.popupService.openSnackBar(
                'TJob Execution ' + this.tJobExec.id + ' finished with status ' + this.tJobExec.result,
              );
            }
          },
          (error: Error) => console.log(error),
        );
      });
    }
  }

  unsubscribeCheckResult(): void {
    if (this.checkResultSubscription !== undefined) {
      this.checkResultSubscription.unsubscribe();
      this.checkResultSubscription = undefined;
    }
  }

  viewTJob(): void {
    this.router.navigate(['/projects', this.tJob.project.id, 'tjob', this.tJobId]);
  }

  stopExec(): void {
    this.disableStopBtn = true;
    this.tJobExecService.stopTJobExecution(this.tJob, this.tJobExec).subscribe(
      (tJobExec: TJobExecModel) => {
        this.tJobExec = tJobExec;
        let id: string = this.tJobExec !== undefined && this.tJobExec !== null ? this.tJobExec.id + ' ' : '';
        let msg: string = 'The execution ' + id + 'has been stopped';
        if (!this.tJobExec.stopped()) {
          msg = 'The execution has finished before stopping it';
        }
        this.popupService.openSnackBar(msg);
      },
      (error: Error) => (this.disableStopBtn = false),
    );
  }
}
