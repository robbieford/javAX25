fontSize = 25;

% Correlation-w_emphasis_0......................24............1
% ZeroCrossing-w_emphasis_0.....................3............0
% StrictZero-w_emphasis_0.......................2............0
% WindowedZero-w_emphasis_0.....................0............0
% PreclockingDemodulator-w_emphasis_0...........17............0
% Goertzel-w_emphasis_0.........................21............0
% Peak-w_emphasis_0.............................0............0
x = [24 3 2 0 17 21 0];
y = ['Correlation' 'Zero Crossing' 'Strict Zero Crossing' 'Windowed Zero Crossing' 'Preclocking' 'Goertzel' 'Peak'];
f = figure('Position',[0,0,1280,1024]);
set(gcf,'color','w');
bar(x);
filename = 'Performance of All Demodulators on OT3 Test with Noise';
title(filename);
xlabel('Demodulator');
ylabel('Number of Packets Decoded');
set(gca,'FontSize',fontSize,'fontWeight','bold');
set(gca,'XTickLabel',{'Correlation', 'Zero Crossing', 'Strict Zero Crossing', 'Windowed Zero Crossing', 'Preclocking', 'Goertzel', 'Peak'})
set(findall(gcf,'type','text'),'FontSize',fontSize,'fontWeight','bold');
rotateXLabels(gca, 45)
set(gca, 'xtick', [])
grid on
saveas(f, strcat('.\..\..\..\rrxthesis\images\',regexprep(filename,'[^\w'']',''),'.png'));
% pause();
% close(f);
% clear all;