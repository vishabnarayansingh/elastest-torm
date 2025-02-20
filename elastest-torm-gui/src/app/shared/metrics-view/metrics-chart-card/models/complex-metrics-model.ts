import { LoadPreviousModel } from '../../../load-previous-view/load-previous-model';
import { LineChartMetricModel } from '../../models/linechart-metric-model';
import { ColorSchemeModel } from '../../models/color-scheme-model';
export class ComplexMetricsModel implements LoadPreviousModel {
  name: string;
  scheme: ColorSchemeModel;
  timeline: boolean;
  leftChart: LineChartMetricModel[];
  rightChartOne: LineChartMetricModel[];
  rightChartTwo: LineChartMetricModel[];

  tooltipDisabled: boolean;
  yLeftAxisTickFormatting;
  yRightOneAxisTickFormatting;
  yRightTwoAxisTickFormatting;

  yLeftAxisScaleFactor;
  yRightAxisScaleFactor;

  gradient: boolean;
  showXAxis: boolean;
  showYAxis: boolean;
  showLegend: boolean;
  legendTitle: string;
  showGridLines: boolean;

  showXAxisLabel: boolean;
  showLeftYAxisLabel: boolean;
  showRightOneYAxisLabel: boolean;
  showRightTwoYAxisLabel: boolean;

  xAxisLabel: string;
  yAxisLabelLeft: string;
  yAxisLabelRightOne: string;
  yAxisLabelRightTwo: string;

  autoScale: boolean;

  prevTraces: string[];
  prevLoaded: boolean;
  hidePrevBtn: boolean;

  startDate: Date;
  endDate: Date;

  constructor() {
    this.name = '';
    this.scheme = new ColorSchemeModel();
    this.timeline = true;
    this.leftChart = [];
    this.rightChartOne = [];
    this.rightChartTwo = [];

    this.tooltipDisabled = false;
    this.yLeftAxisTickFormatting = this.normalFormat;
    this.yRightOneAxisTickFormatting = this.percentFormat;
    this.yRightTwoAxisTickFormatting = this.compressNumberFormat;

    this.yLeftAxisScaleFactor = this.yLeftAxisScale;
    this.yRightAxisScaleFactor = this.yRightAxisScale;

    this.gradient = false;

    this.showXAxis = true;
    this.showYAxis = true;
    this.showLegend = true;
    this.legendTitle = 'Legend';
    this.showGridLines = true;

    this.showXAxisLabel = true;
    this.showLeftYAxisLabel = true;
    this.showRightOneYAxisLabel = true;
    this.showRightTwoYAxisLabel = true;

    this.xAxisLabel = '';
    this.yAxisLabelLeft = '';
    this.yAxisLabelRightOne = '';
    this.yAxisLabelRightTwo = '';

    this.autoScale = true;

    this.prevTraces = [];
    this.prevLoaded = false;
    this.hidePrevBtn = false;
  }

  loadPrevious(): void {}

  onSelect(event): void {}

  normalFormat(data): any {
    return `${data.toLocaleString()}`;
  }

  percentFormat(data): any {
    return `${data}%`;
  }

  compressNumberFormat(data): any {
    let base = Math.floor(Math.log(Math.abs(data)) / Math.log(1000));
    let suffix = 'kmb'[base - 1];
    return suffix ? String(data / Math.pow(1000, base)).substring(0, 3) + suffix : '' + data;
  }

  yLeftAxisScale(min, max): any {
    return { min: `${min}`, max: `${max}` };
  }

  yRightAxisScale(min, max): any {
    return { min: `${min}`, max: `${max}` };
  }
}
