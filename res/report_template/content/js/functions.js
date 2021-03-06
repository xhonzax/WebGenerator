var dataBasedOnTime = [responseTimePercentilesOverTime,
                       responseTimeVsRequest, 
                       responseTimesOverTime, 
                       latenciesOverTime, 
                       bytesThroughputOverTime, 
                       transactionsPerSecond, 
                       codesPerSecond, 
                       hitsPerSecond, 
                       activeThreadsOverTime, 
                       connectTimeOverTime];

var lineChartData = {
        labels: [],
        datasets: [{
        }]
    };

var myChart;

    var canvasData = {data: lineChartData,
            options: {
                type: 'line',
                responsive: true,
                hoverMode: 'index',
                scales: {
                    yAxes: [{
                        type: "linear",
                        display: true,
                        position: "left",
                    }],
                    xAxes: [{
                        type: "linear",
                        display: true,
                        position: "bottom",
                    }]
                }
    }};

function bubbleSort(a)
{
        var swapped;
        do {
            swapped = false;
            for (var i=0; i < a.length-1; i++) {
                if (a[i][0] > a[i+1][0]) {
                    var temp = a[i];
                    a[i] = a[i+1];
                    a[i+1] = temp;
                    swapped = true;
                }
            }
        } while (swapped);
}
 

function drawGraph(typeOfGraph)
{
    Chart.defaults.global.legend.position = "bottom";
    switch(typeOfGraph){
        case "basedOnTime":
            fillLineChartData();
            typeOfGraph = typeOfGraph + "_canvas";
            var ctx = document.getElementById(typeOfGraph).getContext("2d");
            myChart = Chart.Line(ctx, canvasData);
            window.myLine = myChart;
            //GenerovĂˇnĂ­ vlastnĂ­ legendy
            document.getElementById('js-legend').innerHTML = myChart.generateLegend();
            //ProchĂˇzenĂ­ legendy a pĹ™idĂˇnĂ­ pĹ™eĹˇkrtnutĂ­
            var firstVariantyUL = $("#js-legend");
                if (firstVariantyUL.length) {
                var objects = $("#js-legend li");
                objects.each(function() {
                    var index = $(this).index();
                    if(myChart.config.data.datasets[index].hidden){
                        $(this).toggleClass("strike");
                    }
                });
            }
            $("#js-legend > ul > li").on("click",function(e){
                            var index = $(this).index();
                            $(this).toggleClass("strike")
                            var ci = e.view.myChart;
                            var meta = ci.getDatasetMeta(index);
                            var curr = ci.config.data.datasets[index];
                            curr.hidden = !curr.hidden;
                            ci.update();
                        })
            break;
        case "percentiles":
            genPercentileChart();
            typeOfGraph = typeOfGraph + "_canvas";
            var ctx = document.getElementById(typeOfGraph).getContext("2d");
            window.myLine = Chart.Line(ctx, canvasData);
            break;
        case "timeVSThreads":
            genTimeVSThreadsChart();
            typeOfGraph = typeOfGraph + "_canvas";
            var ctx = document.getElementById(typeOfGraph).getContext("2d");
            window.myLine = Chart.Line(ctx, canvasData);
            break;
        case "responseTimeDistribution":
            genresponseTimeDistributionChart();
            typeOfGraph = typeOfGraph + "_canvas";
            var ctx = document.getElementById(typeOfGraph).getContext("2d");
            window.myLine = Chart.Line(ctx, canvasData);
            break;
        case "LatencyVsRequest":
            genLatencyVsRequestChart();
            typeOfGraph = typeOfGraph + "_canvas";
            var ctx = document.getElementById(typeOfGraph).getContext("2d");
            window.myLine = Chart.Line(ctx, canvasData);
            break;
        default:
            console.error("Unknown type of chart: " + typeOfGraph);
            break;
    }
};

var functionCall = 0; // PoÄŤet volĂˇnĂ­ funkce pro generovĂˇnĂ­ barvy
var arraySize = 0; // GlobĂˇlnĂ­ pomocnĂˇ promÄ›nnĂˇ pro vytvoĹ™enĂ­ pole pro labels

//Dynamicky vytvoĹ™enĂ˝ obsah pro graf
//Podle poÄŤtu prvkĹŻ v poli dataArray pĹ™idĂˇm do lineChartData.datasets urÄŤitĂ˝ poÄŤet objektĹŻ, korespondujĂ­cĂ­ch s daty v poli dataArray
function createVariables(dataArray){
    functionCall = 0;
    lineChartData.datasets = [];
    lineChartData.labels = [];

    var count = 0;
  for (var i = 0; i < dataArray.length; ++i) {
    for(x = 0; x < dataArray[i].result.series.length; x++){
      var color = getRandomColor();
      var label = dataArray[i].result.title;
      var borderColor = deleteTransparency(color);
      var backgroundColor = color;
      var fill = true;
      var data = [];
      var min = 0;
      var max = dataArray[i].result.granularity;
      var ticks = {min, max};
      var hidden = true;
      lineChartData.datasets[count] = {label, borderColor, backgroundColor, fill, data, ticks, hidden};
      count++;
    }
  }
  arraySize = count;
}

function genPercentileChart(){
    var data = [responseTimePercentiles];
    createVariables(data);
    var x = 0;
    var y = 0;
    pozice = 0;
    canvasData.options = {
        legend:{
            display:true
        }
    }
    canvasData.options.scales = {
                yAxes: [{
                        type: "linear",
                        display: true,
                        position: "left",
                        scaleLabel: {
                            display: true,
                            labelString: 'Time [ms]'
                        }
                    }],
                    xAxes: [{
                        type: "linear",
                        display: true,
                        position: "bottom",
                        scaleLabel: {
                            display: true,
                            labelString: 'Percentile [%]'
                        }
                    }]
    };

    for(b = 0; b<data.length; b++){
      for(c = 0; c < data[b].result.series.length; c++){
        for(i = 0; i<data[b].result.series[c].data.length;i++){
            var pole = data[b].result.series[c].data[i];
            a = 0;
            each(pole, function(name) {
                if(a == 0){
                            x = name;
                }
                if(a == 1){
                       y = name;
                       lineChartData.datasets[b+c].data[pozice] = {x, y};
                    i++;
                }
                a++;
            });
            ++pozice;
            lineChartData.datasets[b+c].label = data[b].result.title + " | " + data[b].result.series[c].label;
            if(c == 0){
                lineChartData.datasets[b+c].hidden = false;
            }else{
                lineChartData.datasets[b+c].hidden = true;
            }
        }
      }
    }
    function each(array, pFunction) {
    for(var i = 0; i < array.length; i++) {
        var element = array[i];
        pFunction(element);
    }
    }
}

function genLatencyVsRequestChart(){
    var data = [latencyVsRequest];
    createVariables(data);
    var labelsArray = [];
    var labelsA = [];
    var x = 0;
    var y = 0;
    pozice = 0;
    canvasData.options = {
        legend:{
            display:true
        }
    }
    canvasData.options.scales = {
                yAxes: [{
                        type: "linear",
                        display: true,
                        position: "left",
                        label: "Threads",
                        scaleLabel: {
                            display: true,
                            labelString: 'Latency [ms]'
                        }
                    }],
                xAxes: [{
                    scaleLabel: {
                            display: true,
                            labelString: 'Number Of Requests [Request]'
                        }
                }]
    };
    for(b = 0; b<data.length; b++){
      for(c = 0; c < data[b].result.series.length; c++){
        lineChartData.labels = [];
        pozice = 0;
        bubbleSort(data[b].result.series[c].data);
        for(i = 0; i<data[b].result.series[c].data.length;i++){
            var pole = data[b].result.series[c].data[i];
            a = 0;
            each(pole, function(name) {
                if(a == 0){
                        labelsA[pozice] = name;
                }
                if(a == 1){
                       y = name;
                       lineChartData.datasets[b+c].data[pozice] = y;
                    i++;
                }
                a++;
            });
            ++pozice;
            lineChartData.datasets[b+c].label = data[b].result.title + " | " + data[b].result.series[c].label;
            labelsArray[b+c] = labelsA;
            if(c == 0){
                lineChartData.datasets[b+c].hidden = false;
            }else{
                lineChartData.datasets[b+c].hidden = true;
            }
        }
      }
    }
    var arrayPosition = 0;
    var aSize = labelsArray[0].length;
    for(i = 0; i < labelsArray.length; i++){
        if(aSize < labelsArray[i].length){
            aSize = labelsArray[i].length;
            arrayPosition = i;
        }
    }
    lineChartData.labels = labelsArray[arrayPosition];

    function each(array, pFunction) {
    for(var i = 0; i < array.length; i++) {
        var element = array[i];
        pFunction(element);
    }
    }
}

function genTimeVSThreadsChart(){
    var data = [timeVsThreads];

    createVariables(data);
    var labelsArray = [];
    var labelsA = [];
    var x = 0;
    var y = 0;
    pozice = 0;
    canvasData.options = {
        legend:{
            display:true
        }
    }
    canvasData.options.scales = {
                yAxes: [{
                        type: "linear",
                        display: true,
                        position: "left",
                        label: "Threads",
                        scaleLabel: {
                            display: true,
                            labelString: 'Number of threads [Threads]'
                        }
                    }],
                xAxes: [{
                    scaleLabel: {
                            display: true,
                            labelString: 'Time [ms]'
                        }
                }]
    };
    for(b = 0; b<data.length; b++){
      for(c = 0; c < data[b].result.series.length; c++){
        lineChartData.labels = [];
        pozice = 0;
        bubbleSort(data[b].result.series[c].data);
        for(i = 0; i<data[b].result.series[c].data.length;i++){
            var pole = data[b].result.series[c].data[i];
            a = 0;
            each(pole, function(name) {
                if(a == 0){
                        labelsA[pozice] = Math.round(name);
                }
                if(a == 1){
                       y = name;
                       lineChartData.datasets[b+c].data[pozice] = y;
                    i++;
                }
                a++;
            });
            ++pozice;
            lineChartData.datasets[b+c].label = data[b].result.title + " | " + data[b].result.series[c].label;
            labelsArray[b+c] = labelsA;
            if(c == 0){
                lineChartData.datasets[b+c].hidden = false;
            }else{
                lineChartData.datasets[b+c].hidden = true;
            }
        }
      }
    }
    var arrayPosition = 0;
    var aSize = labelsArray[0].length;
    for(i = 0; i < labelsArray.length; i++){
        if(aSize < labelsArray[i].length){
            aSize = labelsArray[i].length;
            arrayPosition = i;
        }
    }
    lineChartData.labels = labelsArray[arrayPosition];

    function each(array, pFunction) {
    for(var i = 0; i < array.length; i++) {
        var element = array[i];
        pFunction(element);
    }
    }
}

function genresponseTimeDistributionChart(){
    var data = [responseTimeDistribution];
    createVariables(data);
    var labelsArray = [];
    var labelsA = [];
    var x = 0;
    var y = 0;
    pozice = 0;
    canvasData.options = {
        legend:{
            display:true
        }
    }
    canvasData.options.scales = {
                yAxes: [{
                        type: "linear",
                        display: true,
                        position: "left",
                        label: "Threads",
                        scaleLabel: {
                            display: true,
                            labelString: 'Response time [ms]'
                        }
                    }],
                xAxes: [{
                    scaleLabel: {
                            display: true,
                            labelString: 'Time [ms]'
                        }
                }]
    };
    for(b = 0; b<data.length; b++){
      for(c = 0; c < data[b].result.series.length; c++){
        lineChartData.labels = [];
        pozice = 0;
        bubbleSort(data[b].result.series[c].data);
        for(i = 0; i<data[b].result.series[c].data.length;i++){
            var pole = data[b].result.series[c].data[i];
            a = 0;
            each(pole, function(name) {
                if(a == 0){
                        labelsA[pozice] = name;
                }
                if(a == 1){
                       y = name;
                       lineChartData.datasets[b+c].data[pozice] =  y;
                    i++;
                }
                a++;
            });
            ++pozice;
            lineChartData.datasets[b+c].label = data[b].result.title + " | " + data[b].result.series[c].label;
            labelsArray[b+c] = labelsA;
            if(c == 0){
                lineChartData.datasets[b+c].hidden = false;
            }else{
                lineChartData.datasets[b+c].hidden = true;
            }
        }
      }
    }
    var arrayPosition = 0;
    var aSize = labelsArray[0].length;
    for(i = 0; i < labelsArray.length; i++){
        if(aSize < labelsArray[i].length){
            aSize = labelsArray[i].length;
            arrayPosition = i;
        }
    }
    lineChartData.labels = labelsArray[arrayPosition];

    function each(array, pFunction) {
    for(var i = 0; i < array.length; i++) {
        var element = array[i];
        pFunction(element);
    }
    }
}

function fillLineChartData(){
    createVariables(dataBasedOnTime);
    var dataArray = dataBasedOnTime;
    var labelsArray = [];
    var labelsA = [];
    var x = 0;
    var y = 0;
    pozice = 0;
    var d = 0;
    canvasData.options = {
        legend:{
            display:false
        }
    }
    canvasData.options.scales = {
                yAxes: [{
                        type: "linear",
                        display: true,
                        position: "left",
                        label: "Threads",
                        scaleLabel: {
                            display: true,
                            labelString: 'According to choose item below'
                        }
                    }],
                xAxes: [{
                    type: "time",
                    scaleLabel: {
                            display: true,
                            labelString: 'Time [m:s]'
                        }
                }]
    };
    for(b = 0; b<dataArray.length; b++){
     for(c = 0; c<dataArray[b].result.series.length; c++){
        lineChartData.labels = [];
        pozice = 0;
        bubbleSort(dataArray[b].result.series[c].data);
        for(i = 0; i<dataArray[b].result.series[c].data.length;i++){
            var pole = dataArray[b].result.series[c].data[i];
            a = 0;
            each(pole, function(name) {
                if(a == 0){
                    var timeData = name + (parseInt(timeZoneOffset)/10);
                    x = moment(timeData).format();
                }
                if(a == 1){
                       y = name;
                       lineChartData.datasets[d].data[pozice] = {y, x};
                    i++;
                }
                a++;
            });
            ++pozice;
            labelsArray[d] = labelsA;
            lineChartData.datasets[d].label = dataArray[b].result.title + " | " + dataArray[b].result.series[c].label;
             if(c == 0 && d == 0){
                lineChartData.datasets[d].hidden = false;
            }else{
                lineChartData.datasets[d].hidden = true;
            }
        }
        d++;
     }
    }

    function each(array, pFunction) {
    for(var i = 0; i < array.length; i++) {
        var element = array[i];
        pFunction(element);
    }
    }
}

function drawPieGraph(){
    n_err =  summaryData.KoPercent.toString();
    n_suc = summaryData.OkPercent.toString();

    var config = {
        type: 'pie',
        data: {
            datasets: [{
                data: [
                    n_err.substr(0,5),
                    n_suc.substr(0,5)
                ],
                backgroundColor: [
                    '#ff0000',
                    '#008000'
                ],
                label: 'Dataset 1'
            }],
            labels: [
                "Errors [%]",
                "Success [%]",
            ]
        },
        options: {
            responsive: false
        }
    };

    window.onload = function() {
        var ctx = document.getElementById("error").getContext("2d");
        window.myPie = new Chart(ctx, config);
    };
}

//ZmÄ›na CSS za bÄ›hu
function changeCSS(cssFile, cssLinkIndex) {

    var oldlink = document.getElementsByTagName("link").item(cssLinkIndex);

    var newlink = document.createElement("link");
    newlink.setAttribute("rel", "stylesheet");
    newlink.setAttribute("type", "text/css");
    newlink.setAttribute("href", cssFile);

    document.getElementsByTagName("head").item(0).replaceChild(newlink, oldlink);
}

// GenerovĂˇnĂ­ nĂˇhodnĂ© barvy pro graf -- generuje HEX kĂłd, kterĂ˝ pĹ™evede na DEC -- prĹŻhlednost je nastavena na 10%

function getRandomColor() {
    var colorArray = ["4D4D4D", //seda 0
    "074DB1", //modra 1 0
    "FAA43A", //oranzova 2
    "69AA00", //zelena 3 0
    "B250A8", //ruzova 4 0
    "553F18", //hneda 5 0
    "3600E8", //fialova 6 0
    "FFB900", //zluta 7 0
    "FF0000", //cervena 8 0
    "099687", // Brcalova 9 0
    "00FFF4"]; //Azurova 10 0
    colorC = colorArray[functionCall];

    var letters = '0123456789ABCDEF';
    var color = 'rgba(';
    var hexString = "";

    for(i = 0; i < colorC.length; i++){
        hexString += colorC[i];
        if(i == 1 || i == 3 || i == 5){
            color += parseInt(hexString, 16);
            hexString = "";
            color += ", "
        }
    }

    color += '0.1)';
    functionCall++;
    if(functionCall == 11){
        functionCall = 0;
    }

    return color;
}

//NastavĂ­ transparentnost na 1
function deleteTransparency(color){
    var test = color.split("");
    for(var i = test.length-1; i > test.length-5; i--){
        delete test[i];
    }
    test = test.join('');
    test += "1)";
    return test;
}

//VykreslĂ­ tabulku s nejÄŤastÄ›jĹˇĂ­mi chybami
function drawErrTable(data){
    var error_data = JSON.parse(data);

        $(document).ready(function()
        {
                    var tableBody = $(document.createElement('tbody'));
                    var row = $(document.createElement('tr'));
                    var newRow = row.clone();
                    tableBody.append(newRow);

                     for (var j = 0; j < error_data.titles.length; j++) {
                     var cell = $(document.createElement('th')).html(error_data.titles[j]);
                     newRow.append(cell.clone());
                     }
                     var newRow = row.clone();

                     for(var i = 0; i < error_data.items.length; i++){
                        tableBody.append(newRow);
                        for (var j = 0; j < error_data.items[i].data.length; j++){
                            if(j == 2 || j == 3){
                                var cislo = error_data.items[i].data[j];
                                cislo = cislo * 100;
                                cislo = Math.round(cislo);
                                cislo = cislo / 100;
                                var cell = $(document.createElement('td')).html(cislo + " %");
                                newRow.append(cell.clone());
                            }else{
                                var cell = $(document.createElement('td')).html(error_data.items[i].data[j]);
                                newRow.append(cell.clone());
                            }
                        }
                    var newRow = row.clone();
                    }

                    $('#mainTable').append(tableBody);
        });
}

function generateTime(begin, end){
    begin = begin.replace('"', '');
    begin = begin.replace('"', '');
    document.getElementById("begin_date").innerHTML = begin;

    

    end = end.replace('"', '');
    end = end.replace('"', '');
    document.getElementById("end_date").innerHTML = end;

    var momentBegin = moment(begin);
    var momentEnd = moment(end);

    print_duration = momentEnd.diff(momentBegin, 'minutes');

    document.getElementById("duration").innerHTML = print_duration + " mins";
}

function fillData(){
    n_err =  summaryData.KoPercent.toString();
    document.getElementById("n_err").innerHTML = n_err.substr(0, 5) + "%";
    document.getElementById("n_usr").innerHTML = dataResult[dataResult.length-1][1];
    document.getElementById("n_samples").innerHTML =  dataResult[dataResult.length-1][2];
    document.getElementById("n_latence").innerHTML =  Round(dataResult[dataResult.length-1][11],2);
    document.getElementById("n_bandw").innerHTML =  Round(dataResult[dataResult.length-1][8],2);
}

function fillURL(){
    document.getElementById("tested_URL").innerHTML =  dataResult[0][15][0];
    document.getElementById("tested_URL").href= dataResult[0][15][0];
}

function fillErrStats(){
    n_err =  summaryData.KoPercent.toString();
    n_succ =  summaryData.OkPercent.toString();
    document.getElementById("errSuccess").innerHTML = n_succ.substr(0, 5) + "%";
    document.getElementById("errFailed").innerHTML = n_err.substr(0, 5) + "%";
}

function Round(num, precision){
  var val = precision!=undefined?Math.pow(10, precision):1;
  return Math.round(num*val)/val;
}

function createStatisticsTable(){
    var titles = ["Request Label", "#Users", "#Samples", "Average", "Min", "Max", "Std. Dev.", "Error %", "Throughput", "Recieved KB/s", "Sent KB/s", "Average bytes/s", "Latency", "Avg. Connect time", "Avg. Response Time (ms)", "URLs"];

     $(document).ready(function()
        {
                    var tableBody = $(document.createElement('tbody'));
                    var row = $(document.createElement('tr'));
                    var newRow = row.clone();
                    tableBody.append(newRow);

                     for (var j = 0; j < titles.length; j++) {
                     var cell = $(document.createElement('th')).html(titles[j]);
                     newRow.append(cell.clone());
                     }
                     var newRow = row.clone();

                     for(var i = 0; i < dataResult.length; i++){
                        tableBody.append(newRow);
                        for (var j = 0; j < dataResult[i].length; j++){
                            if(j == 3 || j == 6 ||j == 5 || j == 8 || j == 9 || j == 10 || j == 11 || j == 7){
                                var cell = $(document.createElement('td')).html(Round(dataResult[i][j],2));
                                newRow.append(cell.clone());
                            }else{
                                if(((j+1) == dataResult[i].length) && ((i) != dataResult.length)){
                                    var urls = dataResult[i][j].toString();
                                    for(m = 0; m < dataResult[i][j].length; m++){
                                        urls = urls.replace(',', '<br>');
                                    }
                                    var cell = $(document.createElement('td')).html(
                                         "<button data-toggle='collapse' class='btn btn-danger dropdown-toggle' data-target='#demo" + i + "'>Show URLs</button>"+
                                        "<div id='demo" + i +"' class='collapse'>"+
                                         urls +
                                        "</div>"
                                     );
                                     newRow.append(cell.clone());
                                }else{
                                var cell = $(document.createElement('td')).html(dataResult[i][j]);
                                newRow.append(cell.clone());
                            }
                            }
                        }
                    var newRow = row.clone();
                    }

                    $('#mainTable').append(tableBody);
        });
}

function createPercentilTable(){
    var titles = ["Request Label", "Percentil 90%", "Percentil 95%", "Percentil 99%"];

     $(document).ready(function()
        {

                    var tableBody = $(document.createElement('tbody'));
                    var row = $(document.createElement('tr'));
                    var newRow = row.clone();
                    tableBody.append(newRow);

                     for (var j = 0; j < titles.length; j++) {
                     var cell = $(document.createElement('th')).html(titles[j]);
                     newRow.append(cell.clone());
                     }
                     var newRow = row.clone();

                     for(var i = 0; i < percentile.length; i++){
                        tableBody.append(newRow);
                        for (var j = 0; j < percentile[i].length; j++){
                            var cell = $(document.createElement('td')).html(percentile[i][j]);
                            newRow.append(cell.clone());
                        }
                    var newRow = row.clone();
                    }
                    $('#percentilTable').append(tableBody);
        });
}

function drawBarGraph(){

    var config = {
        type: 'bar',
        data: {
            datasets: [],
            labels: []
        },
        options: {
            scales: {
                 yAxes: [{
                        scaleLabel: {
                            display: true,
                            labelString: 'Time [ms]'
                        }
                    }],
                xAxes: [{
                    scaleLabel: {
                            display: true,
                            labelString: 'Percentile [%]'
                        }
                }]
            },
            responsive: false
        }
    };

    for(var i = 0; i < percentile.length; i++){
        var color = getRandomColor();
        var borderColor = deleteTransparency(color);
        var dataset = {data:[], backgroundColor:[], hoverBackgroundColor:[], label:[]};
        for(y = 0; y < percentile[i].length; y++){
            if(y == 0){
                dataset.label = percentile[i][y];
                config.data.labels[0] = "Percentile 90%";
                config.data.labels[1] = "Percentile 95%";
                config.data.labels[2] = "Percentile 99%";
                dataset.backgroundColor[0] = borderColor;
                dataset.backgroundColor[1] = borderColor;
                dataset.backgroundColor[2] = borderColor;
                dataset.hoverBackgroundColor[0] = borderColor;
                dataset.hoverBackgroundColor[1] = borderColor;
                dataset.hoverBackgroundColor[2] = borderColor;
            }else{
                dataset.data[y-1] = percentile[i][y];
            }
        }
            config.data.datasets[i] = dataset;
    }

    window.onload = function() {
        var ctx = document.getElementById("percentil").getContext("2d");
        window.myPie = new Chart(ctx, config);
    };
}