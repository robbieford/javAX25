fontSize = 20;

%y = value...?
fle = wavread('.\..\..\nogit\01Track_32bit_48000Hz.wav');
y = fle(593001:593500,1);
clear fle

f = figure('Position',[0,0,1280,1024]);
set(gcf,'color','w');
plot(y);
filename = 'Los Angeles Recording Segment';
title(filename);
xlabel('Sample Number');
ylabel('Magnitude');
minY = min(y);
maxY = max(y);
center = (minY+maxY)/ 2;
rangeY = maxY - minY;
adjustedRange = 0.55*rangeY;
minY = (center - adjustedRange);
maxY = (center + adjustedRange);
ylim([minY maxY]);
set(gca,'FontSize',fontSize, 'FontName', 'Times New Roman');
set(findall(gcf,'type','text'),'FontSize',fontSize, 'FontName', 'Times New Roman');
yL = get(gca,'YLim');
saveas(f, strcat('.\..\..\..\rrxthesis\images\',regexprep(filename,'[^\w'']',''),'.png'));
pause();
close(f);
clear all;