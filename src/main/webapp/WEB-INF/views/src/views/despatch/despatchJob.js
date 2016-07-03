/**
 * Created by Jeng on 2016/1/8.
 */
define(function () {
    return ["$scope", "OrderAPI", "$modal", "$ugDialog","ExpressAPI", function($scope, OrderAPI, $modal, $ugDialog,ExpressAPI){
        $scope.orderList = [];
        $scope.pageInfoSetting = {
            pageSize:10,
            pageNum:1
        };
        $scope.queryParam = {};
        $scope.getOrderList = function(){
            //查询待配送的订单
            OrderAPI.query({
                limit:$scope.pageInfoSetting.pageSize,
                offset:$scope.pageInfoSetting.pageNum,
                keyword:$scope.queryParam.keyword,
                orderStatus:1
            }, function(data){
                $scope.orderList = data.data;
                $scope.pageInfoSetting = data.pageInfo;
                $scope.pageInfoSetting.loadData = $scope.getOrderList;
            });
        };

        $scope.pageSetting = {
            pageSize:10,
            pageNum:1
        };
        //查询快递商
        $scope.expressList = [];
        $scope.getExpressList = function(){
            ExpressAPI.query({
                limit:$scope.pageSetting.pageSize,
                offset:$scope.pageSetting.pageNum,
                keyword:$scope.queryParam.keyword
            },function(data){
                $scope.expressList = data.data;
            });
        }
        //选择快递商
        $scope.choseExpressUser = function(index){
            $scope.currentCustomer = $scope.expressList[index];
        };
        $scope.getOrderList();
        $scope.getExpressList();

        $scope.bindExpress = function(index){
            if(!$scope.currentCustomer){
                $ugDialog.warn("请选择运输的快递商");
                return;
            }
            var orderNos = [];
            orderNos.push($scope.orderList[index].orderNo);
            ExpressAPI.bindExpress({
                expressId:$scope.currentCustomer.id,
                orderNos:orderNos
            }, function(){
                $scope.getOrderList();
                $scope.getCustomerManagersList();
            })
        };
        $scope.unbindExpress = function(index){
            var orderNos = [];
            orderNos.push($scope.orderList[index].orderNo);
            ExpressAPI.unbindExpress({
                orderNos:orderNos
            }, function(){
                $scope.getOrderList();
                $scope.getCustomerManagersList();
            })
        }
    }];
});